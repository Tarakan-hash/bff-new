#!/usr/bin/env python3
"""Проверка миграций и реального SQL outbox на изолированном PostgreSQL (Docker)."""
import pathlib
import re
import subprocess
import time
import uuid

root = pathlib.Path(__file__).resolve().parents[2]
service = root / 'others/crm-integration-service'
name = 'bfflk-crm-outbox-' + uuid.uuid4().hex[:10]

def docker(*args, **kwargs):
    result = subprocess.run(['docker', *args], capture_output=True, text=True, **kwargs)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return result.stdout.strip()

def sql(statement):
    return docker('exec', '-i', name, 'psql', '-U', 'postgres', '-v', 'ON_ERROR_STOP=1', '-At', input=statement)

try:
    docker('run', '-d', '--name', name, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'postgres:17-alpine')
    for attempt in range(50):
        if subprocess.run(['docker', 'exec', name, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'], capture_output=True).returncode == 0:
            break
        time.sleep(0.1)
    for migration in sorted((service / 'src/main/resources/db/migration').glob('*.sql')):
        sql(migration.read_text())
    repository = (service / 'src/main/java/sb/broker/integration/crm/repository/OutboxEventRepository.java').read_text()
    query = re.search(r'value = """(.*?)"""', repository, re.S).group(1)
    creation, update = str(uuid.uuid4()), str(uuid.uuid4())
    def insert(request, event, parent='NULL'):
        sql(f"""INSERT INTO outbox_event(id,payload,status,attempts,next_attempt_at,created_at,event_type,request_id,create_request_id)
            VALUES ('{uuid.uuid4()}','{{}}','PENDING',0,now(),now(),'{event}','{request}',{parent})""")
    # Ожидающее подтверждения обновление стоит раньше остальных, но не блокирует ready-команды.
    insert(update, 'LEAD_UPDATED', "'" + creation + "'")
    assert sql(query) == '', 'Update without leadId must not be claimed'
    insert(creation, 'LEAD_CREATED')
    assert creation in sql(query), 'Waiting update must not block creation'
    sql(f"UPDATE outbox_event SET status='SENT' WHERE request_id='{creation}'")
    assert sql(query) == '', 'Sending create is not confirmation from CRM'
    reference_repository = (service / 'src/main/java/sb/broker/integration/crm/repository/CrmLeadReferenceRepository.java').read_text()
    confirm = re.search(r'value = """(.*?)"""', reference_repository, re.S).group(1)
    confirm = confirm.replace(':requestId', "'" + creation + "'").replace(':leadId', '100500')
    sql(confirm)
    sql(confirm)
    assert update in sql(query), 'Confirmed update must become claimable'
    assert sql('SELECT count(*) FROM crm_lead_reference') == '1', 'Repeated confirmation must be idempotent'
    sql(f"UPDATE outbox_event SET status='SENT' WHERE request_id='{update}'")
    insert(str(uuid.uuid4()), 'LEAD_UPDATED')
    assert sql(query) == '', 'Historical uncorrelated updates must fail closed'
    print('PASS: migrations, waiting update, no head-of-line blocking, confirmation, retry, legacy guard')
finally:
    subprocess.run(['docker', 'rm', '-f', name], check=False, capture_output=True)
