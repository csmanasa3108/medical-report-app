insert into patient_clinician_access (
    id,
    patient_user_id,
    clinician_user_id,
    status,
    created_at
)
select
    '00000000-0000-0000-0000-000000000201'::uuid,
    patient.id,
    clinician.id,
    'ACTIVE',
    now()
from app_users patient
cross join app_users clinician
where patient.id = '00000000-0000-0000-0000-000000000101'::uuid
    and clinician.id = '00000000-0000-0000-0000-000000000102'::uuid
on conflict (patient_user_id, clinician_user_id) do update
set status = 'ACTIVE';
