alter table lab_observations
    add column source_report_id uuid,
    add column source_parsed_observation_id uuid;

alter table lab_observations
    add constraint fk_lab_observations_source_report
        foreign key (source_report_id) references reports(id),
    add constraint fk_lab_observations_source_parsed_observation
        foreign key (source_parsed_observation_id) references parsed_observations(id);

create index idx_lab_observations_source_report_id
    on lab_observations(source_report_id);

create index idx_lab_observations_source_parsed_observation_id
    on lab_observations(source_parsed_observation_id);
