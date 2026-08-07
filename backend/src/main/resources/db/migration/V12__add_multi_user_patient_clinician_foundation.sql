create table if not exists app_users (
    id uuid primary key,
    email varchar(255) not null unique,
    display_name varchar(255) not null,
    role varchar(50) not null check (role in ('PATIENT', 'CLINICIAN')),
    created_at timestamptz not null default now()
);

create table if not exists patient_clinician_access (
    id uuid primary key,
    patient_user_id uuid not null references app_users(id),
    clinician_user_id uuid not null references app_users(id),
    status varchar(50) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    constraint patient_clinician_access_status_check check (status in ('ACTIVE')),
    constraint uq_patient_clinician_access unique (patient_user_id, clinician_user_id)
);

insert into app_users (id, email, display_name, role)
values
    ('00000000-0000-0000-0000-000000000101', 'patient.demo@soverahealth.local', 'Demo Patient', 'PATIENT'),
    ('00000000-0000-0000-0000-000000000102', 'clinician.demo@soverahealth.local', 'Demo Clinician', 'CLINICIAN')
on conflict (id) do update
set
    email = excluded.email,
    display_name = excluded.display_name,
    role = excluded.role;

insert into patient_clinician_access (id, patient_user_id, clinician_user_id, status)
values (
    '00000000-0000-0000-0000-000000000201',
    '00000000-0000-0000-0000-000000000101',
    '00000000-0000-0000-0000-000000000102',
    'ACTIVE'
)
on conflict (patient_user_id, clinician_user_id) do update
set status = excluded.status;

alter table reports
    add column if not exists patient_user_id uuid,
    add column if not exists uploaded_by_user_id uuid;

alter table lab_observations
    add column if not exists patient_user_id uuid,
    add column if not exists created_by_user_id uuid;

update reports
set
    patient_user_id = coalesce(patient_user_id, '00000000-0000-0000-0000-000000000101'),
    uploaded_by_user_id = coalesce(uploaded_by_user_id, '00000000-0000-0000-0000-000000000101')
where patient_user_id is null
    or uploaded_by_user_id is null;

update lab_observations
set
    patient_user_id = coalesce(patient_user_id, '00000000-0000-0000-0000-000000000101'),
    created_by_user_id = coalesce(created_by_user_id, '00000000-0000-0000-0000-000000000101')
where patient_user_id is null
    or created_by_user_id is null;

alter table reports
    add constraint fk_reports_patient_user
        foreign key (patient_user_id) references app_users(id),
    add constraint fk_reports_uploaded_by_user
        foreign key (uploaded_by_user_id) references app_users(id);

alter table lab_observations
    add constraint fk_lab_observations_patient_user
        foreign key (patient_user_id) references app_users(id),
    add constraint fk_lab_observations_created_by_user
        foreign key (created_by_user_id) references app_users(id);

create index if not exists idx_reports_patient_user_id
    on reports(patient_user_id);

create index if not exists idx_reports_uploaded_by_user_id
    on reports(uploaded_by_user_id);

create index if not exists idx_lab_observations_patient_user_id
    on lab_observations(patient_user_id);

create index if not exists idx_lab_observations_created_by_user_id
    on lab_observations(created_by_user_id);

create index if not exists idx_patient_clinician_access_clinician_user_id
    on patient_clinician_access(clinician_user_id);
