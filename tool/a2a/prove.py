"""Bounded loopback proof against official a2a-sdk 1.1.0, no model/provider.

Run with an isolated venv: python tool/a2a/prove.py build/traycer/a2a-proof
The server is owned by this process, bound to an OS-assigned loopback port,
and always shut down in finally. No external agent or credential is read.
"""
import asyncio
import importlib.metadata
import json
import logging
import pathlib
import socket
import sys
import uuid

import httpx
import uvicorn
from starlette.applications import Starlette
from starlette.middleware import Middleware
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import JSONResponse

from a2a.server.agent_execution import AgentExecutor
from a2a.server.request_handlers.default_request_handler_v2 import DefaultRequestHandlerV2
from a2a.server.routes.agent_card_routes import create_agent_card_routes
from a2a.server.routes.jsonrpc_routes import create_jsonrpc_routes
from a2a.server.tasks.inmemory_task_store import InMemoryTaskStore
from a2a.server.tasks.task_updater import TaskUpdater
from a2a.types import a2a_pb2 as pb


class SyntheticAgent(AgentExecutor):
    async def execute(self, context, event_queue):
        task = pb.Task(
            id=context.task_id,
            context_id=context.context_id,
            status=pb.TaskStatus(state=pb.TASK_STATE_SUBMITTED),
            history=[context.message],
        )
        await event_queue.enqueue_event(task)
        updater = TaskUpdater(event_queue, task.id, task.context_id)
        await updater.update_status(pb.TASK_STATE_WORKING)
        if context.get_user_input() == 'wait':
            await asyncio.Event().wait()
        elif context.current_task is None:
            await asyncio.sleep(0.1)
            await updater.update_status(
                pb.TASK_STATE_INPUT_REQUIRED,
                pb.Message(message_id=str(uuid.uuid4()), task_id=task.id,
                           context_id=task.context_id, role=pb.ROLE_AGENT,
                           parts=[pb.Part(text='Which color?')]),
            )
        else:
            await updater.add_artifact(
                [pb.Part(text='Synthetic result: blue', media_type='text/plain'),
                 pb.Part(url='https://example.org/synthetic.txt', filename='synthetic.txt', media_type='text/plain')],
                name='Synthetic result',
            )
            await updater.update_status(pb.TASK_STATE_COMPLETED)

    async def cancel(self, context, event_queue):
        updater = TaskUpdater(event_queue, context.task_id, context.context_id)
        await updater.update_status(pb.TASK_STATE_CANCELED)


class FixtureAuth(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        if request.url.path == '/rpc' and request.headers.get('authorization') != 'Bearer synthetic-fixture-token':
            return JSONResponse({'error': 'Unauthorized'}, status_code=401)
        return await call_next(request)


async def prove(output):
    assert importlib.metadata.version('a2a-sdk') == '1.1.0'
    output.mkdir(parents=True, exist_ok=True)
    listener = socket.socket()
    listener.bind(('127.0.0.1', 0))
    listener.listen(128)
    port = listener.getsockname()[1]
    origin = f'http://127.0.0.1:{port}'
    card = pb.AgentCard(
        name='Synthetic color agent', description='No model; deterministic A2A lifecycle fixture.',
        version='fixture-1',
        supported_interfaces=[pb.AgentInterface(url=f'{origin}/rpc', protocol_binding='JSONRPC', protocol_version='1.0')],
        capabilities=pb.AgentCapabilities(streaming=False),
        default_input_modes=['text/plain'], default_output_modes=['text/plain'],
        skills=[pb.AgentSkill(id='color', name='Choose a color', description='Asks for input then returns an artifact.', tags=['fixture'])],
        security_schemes={'bearer': pb.SecurityScheme(http_auth_security_scheme=pb.HTTPAuthSecurityScheme(scheme='Bearer'))},
        security_requirements=[pb.SecurityRequirement(schemes={'bearer': pb.StringList()})],
    )
    handler = DefaultRequestHandlerV2(SyntheticAgent(), InMemoryTaskStore(), card)
    app = Starlette(routes=create_agent_card_routes(card)+create_jsonrpc_routes(handler, '/rpc'), middleware=[Middleware(FixtureAuth)])
    server = uvicorn.Server(uvicorn.Config(app, log_level='critical', access_log=False, lifespan='off'))
    server_task = asyncio.create_task(server.serve(sockets=[listener]))
    evidence = {}
    try:
        while not server.started:
            if server_task.done():
                await server_task
                raise RuntimeError('Fixture server did not start')
            await asyncio.sleep(0.02)
        async with httpx.AsyncClient(timeout=5, follow_redirects=False) as client:
            response = await client.get(origin+'/.well-known/agent-card.json')
            response.raise_for_status()
            evidence['card'] = response.json()
            assert evidence['card']['supportedInterfaces'][0]['protocolVersion'] == '1.0'
            unauth = await client.post(origin+'/rpc', json={'jsonrpc':'2.0','id':1,'method':'GetTask','params':{'id':'missing'}})
            assert unauth.status_code == 401
            async def rpc(method, params, use_client=client):
                result = await use_client.post(origin+'/rpc', headers={'Authorization':'Bearer synthetic-fixture-token','A2A-Version':'1.0'}, json={'jsonrpc':'2.0','id':str(uuid.uuid4()),'method':method,'params':params})
                result.raise_for_status()
                payload = result.json()
                if 'error' in payload:
                    raise AssertionError(f"Unexpected fixture RPC error: {payload['error'].get('code')}")
                return payload['result']
            def message(text, task=None):
                value={'messageId':str(uuid.uuid4()),'role':'ROLE_USER','parts':[{'text':text}]}
                if task:
                    value.update(taskId=task['id'], contextId=task['contextId'])
                return {'message':value,'configuration':{'returnImmediately':True,'historyLength':10,'acceptedOutputModes':['text/plain']}}
            async def until(task_id, state):
                for _ in range(100):
                    task=await rpc('GetTask', {'id':task_id,'historyLength':10})
                    if task['status']['state']==state:
                        return task
                    await asyncio.sleep(0.02)
                raise AssertionError('Fixture task did not reach expected state')
            sent = await rpc('SendMessage', message('choose'))
            assert 'task' in sent
            evidence['submitted'] = sent
            task = await until(sent['task']['id'], 'TASK_STATE_INPUT_REQUIRED')
            evidence['inputRequired'] = task
            continued = await rpc('SendMessage', message('blue', task))
            assert continued['task']['id'] == task['id']
            completed = await until(task['id'], 'TASK_STATE_COMPLETED')
            assert completed['contextId'] == task['contextId']
            assert completed['artifacts'][0]['parts'][0]['text'] == 'Synthetic result: blue'
            evidence['completed'] = completed
            async with httpx.AsyncClient(timeout=5, follow_redirects=False) as reopened:
                restored = await rpc('GetTask', {'id':task['id'],'historyLength':10}, reopened)
                assert restored['id'] == task['id'] and restored['status']['state'] == 'TASK_STATE_COMPLETED'
            waiting = await rpc('SendMessage', message('wait'))
            working = await until(waiting['task']['id'], 'TASK_STATE_WORKING')
            evidence['working'] = working
            canceled = await rpc('CancelTask', {'id':working['id']})
            assert canceled['id'] == working['id'] and canceled['status']['state'] == 'TASK_STATE_CANCELED'
            evidence['canceled'] = canceled
            terminal = await client.post(origin+'/rpc', headers={'Authorization':'Bearer synthetic-fixture-token','A2A-Version':'1.0'}, json={'jsonrpc':'2.0','id':'terminal','method':'SendMessage','params':message('again', completed)})
            assert 'error' in terminal.json()
            (output/'wire-fixtures.json').write_text(json.dumps(evidence, indent=2), encoding='utf-8')
            print('PASS discovery, bearer401/authorized, submit, progress, same-task/context input, artifact, fresh-client GetTask, cancel, terminal-send rejection', flush=True)
    finally:
        server.should_exit = True
        await asyncio.wait_for(server_task, 5)
        listener.close()
        probe = socket.socket()
        probe.settimeout(0.2)
        try:
            assert probe.connect_ex(('127.0.0.1', port)) != 0
        finally:
            probe.close()
        print('PASS owned loopback listener closed', flush=True)


if __name__ == '__main__':
    logging.disable(logging.CRITICAL)
    asyncio.run(asyncio.wait_for(prove(pathlib.Path(sys.argv[1])), timeout=45))
