type StatusBadgeProps = {
  status: string | null | undefined;
};

function formatStatus(status: string | null | undefined) {
  if (!status) {
    return "Not provided";
  }

  return status.replace(/_/g, " ");
}

function getStatusTone(status: string | null | undefined) {
  switch (status) {
    case "CONFIRMED":
      return "status-badge-confirmed";
    case "NEEDS_REVIEW":
      return "status-badge-review";
    case "TEXT_EXTRACTED":
      return "status-badge-teal";
    case "UPLOADED":
    case "CREATED":
    default:
      return "status-badge-neutral";
  }
}

function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span className={`status-badge ${getStatusTone(status)}`}>
      {formatStatus(status)}
    </span>
  );
}

export default StatusBadge;
