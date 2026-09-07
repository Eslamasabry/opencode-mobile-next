# Local connection help and private networks

Open **Guide → Connection help**. Paste an address and choose **Explain address**.
This is a local syntax/transport-policy explanation, not a connection test. It
does not resolve DNS, probe ports, detect VPNs, contact servers, launch links,
log input or save it. Input is masked and cleared after classification; leaving
disposes its controller. The OS clipboard and keyboard have their own privacy
policies. Copy buttons copy only fixed examples, never pasted content.

The classifier reuses `normalizeServerProfileUrl`, `validateServerProfileUrl`
and `isLoopbackHost` from `lib/state/profiles.dart`. Bare remote hostnames gain
HTTPS; supported bare loopback addresses gain HTTP. Acceptance proves neither
reachability nor server identity, authentication, TLS validity or VPN presence.

## HTTPS over a private network or reverse proxy

1. Keep OpenCode bound to server-side loopback, with authentication on.
2. Join both devices to your intended private network. Restrict access to the
   intended users/devices using its access rules.
3. Configure private HTTPS (for example Tailscale Serve), or a reverse proxy
   with a device-trusted certificate, forwarding to that loopback port. Retain
   server authentication and support streaming/WebSocket connections.
4. Use the HTTPS origin in Servers, with credentials in separate fields.
   `https://server.example` is a placeholder, not a working endpoint.
5. Check DNS, certificate trust, access rules and proxy routing externally from
   the app device before using the normal Servers connection action.

A VPN does not grant an HTTP exception. LAN and `100.64.0.0/10` addresses still
require HTTPS. An address cannot prove VPN presence. **Tailscale Funnel exposes
a service to the public internet**; it is not the private Serve route. Do not
enable Funnel as a private-network fix. Treat server access like shell access;
never disable TLS verification or expose an unauthenticated server as a fix.

## Loopback and tunnels

`localhost`, `127.0.0.1` and `[::1]` mean the device running this app, not the
computer that printed a pairing address. For another computer, use its private
HTTPS origin or terminate an encrypted forward on the app device.

Where an SSH client is available **on the app device**, an example is:

```sh
ssh -N -L 127.0.0.1:4096:127.0.0.1:4096 user@host
```

Replace `user@host` with your SSH destination and verify its host key. Keep the
forward running, then use `http://127.0.0.1:4096` with separate server sign-in.
Running this on another laptop does not create a listener on the phone. Use a
device-side tunnel client if no terminal is available; this app does not install
or verify a tunnel. Termux remains Android-only.

## Localization handoff (lead-owned generated output)

New UI references these `AppLocalizations` getters. Add these English values to
the lead-owned ARB and generate localization before integration. In the table,
`\n` indicates a newline.

| Key | English |
| --- | --- |
| connectionHelpTitle | Connection help |
| connectionHelpEntrySubtitle | Explain an address locally, without connecting |
| connectionHelpGuideTip | Keep the server off the public internet. Use private HTTPS or an encrypted tunnel ending on the device running this app. Localhost on your computer is not localhost on your phone. Open Connection help above for steps and examples. |
| connectionHelpPrivacy | This checks address rules only, not connectivity. Nothing is sent or saved. Input is hidden and cleared after checking. Paste only an address, not a password or pairing code. |
| connectionHelpAddress | Server address |
| connectionHelpCheck | Explain address |
| connectionHelpEmpty | Enter a server address to explain. |
| connectionHelpMalformed | This address could not be understood. Use a complete origin such as https://server.example, with no path, credentials or query. |
| connectionHelpCredentials | Credentials do not belong in a URL. Remove them and enter the server username and password separately in Servers. The pasted value has been cleared. |
| connectionHelpQuery | Remove query parameters and fragments. They can contain secrets; enter only the server origin. The pasted value has been cleared. |
| connectionHelpPath | Remove the path. This app needs the server origin, not a page or API route. |
| connectionHelpScheme | Use HTTPS for a remote server, or HTTP only for this device's supported loopback addresses. |
| connectionHelpRemoteHttp | Remote HTTP is blocked, including LAN and 100.64.0.0/10 addresses. A VPN does not change this rule. Set up private HTTPS or an encrypted tunnel ending on this device. |
| connectionHelpHttps | This address passes the HTTPS address rules. That does not verify its certificate, reachability, sign-in or privacy. A bare remote address is interpreted as HTTPS. |
| connectionHelpLoopback | This address passes the loopback address rules. Localhost means this device, not another computer. A server or tunnel must be listening here; this check does not verify that. |
| connectionHelpPrivateTitle | Private HTTPS or reverse proxy |
| connectionHelpPrivateSteps | 1. Keep the server on its host's loopback with authentication enabled.\n2. Connect both devices to your private network and restrict access to intended users.\n3. Configure private HTTPS, such as Tailscale Serve, or a reverse proxy with a trusted certificate forwarding to the server. Support streaming and WebSockets.\n4. Add the HTTPS origin in Servers with sign-in in separate fields.\nTailscale Funnel exposes the service publicly; it is not a private-network fix. This app cannot infer VPN presence. The example below is a placeholder. |
| connectionHelpTunnelTitle | Localhost on the wrong device? |
| connectionHelpTunnelSteps | Localhost, 127.0.0.1 and [::1] refer to the device running this app. For a server on another computer, use private HTTPS or an encrypted tunnel ending here. If an SSH client is available on this device, adapt the example below, verify the host key and keep it running. Replace user@host with your SSH destination. Running it on another computer does not forward this device's port. Keep server authentication enabled. |
| connectionHelpVerifyTitle | Verify connectivity separately |
| connectionHelpVerifySteps | On this device, check private-network membership, DNS, firewall access and certificate trust using your network tools. Check server and proxy configuration on the host, then use Servers to connect. Never disable TLS verification or share passwords, pairing codes or unredacted logs. Access to this server is shell access. |
| connectionHelpCopyExample | Copy example |
| connectionHelpCopied | Example copied |
| connectionHelpCopyFailed | Could not copy the example. Select the example text to copy it manually. |

## Verification boundary

No profile storage, deletion, connection generations, transport policy or native
networking changes. The only async operation copies an app-authored literal and
checks mounting before feedback. Integration should cover every advice enum,
malformed/oversized input, input clearing, navigation, copy failure, narrow
layouts and large text. Actual connectivity verification remains external.
