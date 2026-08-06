import { FormEvent, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { ReportResponse, uploadReport } from "../api/client";
import StatusBadge from "../components/StatusBadge";

type ReportFormState = {
  reportDate: string;
  labName: string;
};

const initialFormState: ReportFormState = {
  reportDate: "",
  labName: ""
};

function NewReportPage() {
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [form, setForm] = useState<ReportFormState>(initialFormState);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadedReport, setUploadedReport] = useState<ReportResponse | null>(
    null
  );
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  function updateField(field: keyof ReportFormState, value: string) {
    setForm((currentForm) => ({
      ...currentForm,
      [field]: value
    }));
  }

  function handleFileChange(file: File | null) {
    setUploadedReport(null);
    setErrorMessage("");

    if (!file) {
      setSelectedFile(null);
      return;
    }

    if (!file.name.toLowerCase().endsWith(".pdf")) {
      setSelectedFile(null);
      setErrorMessage("Select a PDF file.");
      return;
    }

    setSelectedFile(file);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage("");
    setUploadedReport(null);

    const labName = form.labName.trim();

    if (!selectedFile) {
      setErrorMessage("Select a PDF file to upload.");
      return;
    }

    if (!selectedFile.name.toLowerCase().endsWith(".pdf")) {
      setErrorMessage("Only PDF files can be uploaded.");
      return;
    }

    setIsSubmitting(true);

    try {
      const createdReport = await uploadReport({
        file: selectedFile,
        reportDate: form.reportDate,
        labName
      });

      setUploadedReport(createdReport);
      setSelectedFile(null);
      setForm(initialFormState);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    } catch (error: unknown) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to upload the report."
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
          <h2 className="page-title">Upload Diagnostic Report</h2>
          <p className="page-description">
            Add a PDF report and optional metadata for diagnostic review.
          </p>
        </div>
        <Link className="button-link secondary" to="/reports">
          All Reports
        </Link>
      </div>

      <div className="form-card">
        <form className="metadata-form upload-report-form" onSubmit={handleSubmit}>
          <label className="full-span-field">
            <span>PDF file</span>
            <span className="field-helper">PDF files only</span>
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf,.pdf"
              onChange={(event) =>
                handleFileChange(event.target.files?.item(0) ?? null)
              }
              required
            />
          </label>

          <label>
            Report date <span className="optional-label">Optional</span>
            <input
              type="date"
              value={form.reportDate}
              onChange={(event) => updateField("reportDate", event.target.value)}
            />
          </label>

          <label>
            Lab name <span className="optional-label">Optional</span>
            <input
              type="text"
              value={form.labName}
              onChange={(event) => updateField("labName", event.target.value)}
              placeholder="Quest Diagnostics"
            />
          </label>

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Uploading..." : "Upload report"}
          </button>
        </form>
      </div>

      {uploadedReport ? (
        <div className="status-message success-message" role="status">
          <p>Report uploaded successfully.</p>
          <dl className="upload-summary">
            <div>
              <dt>Filename</dt>
              <dd>{uploadedReport.originalFilename}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={uploadedReport.status} />
              </dd>
            </div>
          </dl>
          <div className="message-actions">
            <Link to={`/reports/${uploadedReport.id}`}>View report</Link>
            <Link to="/reports">Back to reports</Link>
          </div>
        </div>
      ) : null}

      {errorMessage ? (
        <p className="status-message error-message" role="alert">
          {errorMessage}
        </p>
      ) : null}
    </section>
  );
}

export default NewReportPage;
