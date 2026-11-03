//! Live proof of the team engine's OpenCode 2 driver against a real server.
//!
//! Ignored by default: it needs a running `opencode2 serve` and network access
//! for the server's model. Run against a throwaway server only (own XDG dirs,
//! own port, own password), never a person's server:
//!
//! ```text
//! OC2_LIVE_URL=http://127.0.0.1:4231 OC2_LIVE_PASSWORD=... \
//! OC2_LIVE_DIR=/abs/path/to/a/scratch/git/repo \
//! cargo test --test oc2_live -- --ignored --nocapture
//! ```
//!
//! The password is read from the environment and never printed.
use oc_phone_engine::opencode::OpenCodeClient;
use serde_json::Value;
use std::time::{Duration, Instant};

struct Live {
    client: OpenCodeClient,
    dir: String,
}

fn live() -> Live {
    let url = std::env::var("OC2_LIVE_URL").expect("OC2_LIVE_URL");
    let password = std::env::var("OC2_LIVE_PASSWORD").expect("OC2_LIVE_PASSWORD");
    let dir = std::env::var("OC2_LIVE_DIR").expect("OC2_LIVE_DIR");
    Live {
        client: OpenCodeClient::new(&url, "opencode", &password).expect("client"),
        dir,
    }
}

/// Polls the driver's own observation until the turn settles. A free model's
/// usage limit is reported as `quota` so the caller can tell it apart from a
/// driver failure.
async fn settle(live: &Live, id: &str) -> Value {
    let start = Instant::now();
    loop {
        let seen = live.client.observe(&live.dir, id).await.expect("observe");
        let state = seen["state"].as_str().unwrap_or_default().to_owned();
        eprintln!("  observe {id}: {state} ({} s)", start.elapsed().as_secs());
        if matches!(state.as_str(), "completed" | "failed" | "blocked") {
            return seen;
        }
        assert!(
            start.elapsed() < Duration::from_secs(240),
            "turn did not settle in 240 s: {seen}"
        );
        tokio::time::sleep(Duration::from_secs(2)).await;
    }
}

#[tokio::test]
#[ignore = "needs a live OpenCode 2 server; see the module docs"]
async fn driver_runs_planner_and_worker_turns_on_a_live_server() {
    let live = live();
    let verified = live.client.verify().await.expect("verify");
    eprintln!("verify: version {}", verified["version"]);
    assert_eq!(verified["healthy"], true);

    let model = live
        .client
        .resolve_model(&live.dir, "")
        .await
        .expect("default model");
    eprintln!("default model: {model}");

    let mut observer = live
        .client
        .global_status_observer()
        .await
        .expect("event stream");

    // Planner: read-only turn that only has to answer.
    let planner = live
        .client
        .create_session(&live.dir, "AI Team live proof: planner")
        .await
        .expect("create planner session");
    assert!(live
        .client
        .person_directory_status(
            std::slice::from_ref(&live.dir),
            std::slice::from_ref(&planner)
        )
        .await
        .expect("directory status"));
    let accepted = live
        .client
        .prompt(
            &live.dir,
            &planner,
            "planner",
            &model,
            "Answer in one short line. Do not use tools.",
            "Reply with the single word READY.",
            true,
        )
        .await
        .expect("planner prompt");
    assert_eq!(accepted["accepted"], true);

    // Stable OC2 (2.0.29 through 2.2.5) sends no `session.status` frames:
    // busy comes from `session.execution.*`, which carries no location, so
    // the engine keeps polling `/api/session/active` for admission. Here the
    // stream must stay parseable while the turn runs: every frame the server
    // sends during a real turn is read without a protocol error.
    let frames = tokio::spawn(async move {
        let mut seen = 0usize;
        let deadline = Instant::now() + Duration::from_secs(20);
        while Instant::now() < deadline {
            match tokio::time::timeout(Duration::from_secs(5), observer.next()).await {
                Ok(Ok(_)) => seen += 1,
                Err(_) => {}
                Ok(Err(e)) => panic!("event stream failed: {e}"),
            }
        }
        seen
    });

    let seen = settle(&live, &planner).await;
    eprintln!("planner: {} text={:?}", seen["state"], seen["text"]);
    assert_eq!(
        seen["state"], "completed",
        "planner did not complete: {seen}"
    );
    assert!(!seen["text"].as_str().unwrap_or_default().is_empty());
    assert!(
        frames.await.unwrap() > 0,
        "no event frames during the planner turn"
    );

    // Worker: may edit, must write the file through the server's tools.
    let worker = live
        .client
        .create_session(&live.dir, "AI Team live proof: worker")
        .await
        .expect("create worker session");
    live.client
        .prompt(
            &live.dir,
            &worker,
            "worker",
            &model,
            "You are the worker. Make the change, then reply with one short line.",
            "Create a file named LIVE.md in the current folder containing exactly the line: Hello from the live proof",
            false,
        )
        .await
        .expect("worker prompt");
    let seen = settle(&live, &worker).await;
    eprintln!("worker: {} text={:?}", seen["state"], seen["text"]);
    assert_eq!(
        seen["state"], "completed",
        "worker did not complete: {seen}"
    );
    let written = std::fs::read_to_string(format!("{}/LIVE.md", live.dir)).expect("LIVE.md");
    assert!(written.contains("Hello from the live proof"), "{written:?}");

    // Stopping an idle session revokes its tools and confirms it is stopped.
    live.client
        .abort(&live.dir, &worker)
        .await
        .expect("abort idle worker");
}
