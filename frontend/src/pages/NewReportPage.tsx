import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createReport } from "../api/client";

type ReportFormState = {
  originalFilename: string;
  reportDate: string;
  labName: string;
};

const initialFormState: ReportFormState = {
  originalFilename: "",
  reportDate: "",
  labName: ""
};

function NewReportPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<ReportFormState>(initialFormState);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  function updateField(field: keyof ReportFormState, value: string) {
    setForm((currentForm) => ({
      ...currentForm,
      [field]: value
    }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");

    const originalFilename = form.originalFilename.trim();
    const labName = form.labName.trim();

    if (!originalFilename || !form.reportDate || !labName) {
      setErrorMessage("Complete all report metadata fields.");
      return;
    }

    setIsSubmitting(true);

    try {
      const createdReport = await createReport({
        originalFilename,
        reportDate: form.reportDate,
        labName
      });

      navigate(`/reports/${createdReport.id}`);
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to create the report."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="page-section">
      <div className="section-header">
        <div>
          <p className="eyebrow">Reports</p>
          <h2>New Report</h2>
        </div>
        <Link className="button-link secondary" to="/reports">
          All Reports
        </Link>
      </div>

      <form className="metadata-form" onSubmit={handleSubmit}>
        <label>
          Original filename
          <input
            type="text"
            value={form.originalFilename}
            onChange={(event) =>
              updateField("originalFilename", event.target.value)
            }
            placeholder="lab-report-july.pdf"
            required
          />
        </label>

        <label>
          Report date
          <input
            type="date"
            value={form.reportDate}
            onChange={(event) => updateField("reportDate", event.target.value)}
            required
          />
        </label>

        <label>
          Lab name
          <input
            type="text"
            value={form.labName}
            onChange={(event) => updateField("labName", event.target.value)}
            placeholder="Quest Diagnostics"
            required
          />
        </label>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Saving..." : "Save Report"}
        </button>
      </form>

      {errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

export default NewReportPage;
