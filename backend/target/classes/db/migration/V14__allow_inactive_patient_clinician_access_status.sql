ALTER TABLE patient_clinician_access
DROP CONSTRAINT IF EXISTS patient_clinician_access_status_check;

ALTER TABLE patient_clinician_access
ADD CONSTRAINT patient_clinician_access_status_check
CHECK (status IN ('ACTIVE', 'INACTIVE'));
