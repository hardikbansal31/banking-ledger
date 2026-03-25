import React from "react";

export default function Badge({
  children,
  variant = "default",
  className = "",
  ...props
}) {
  // Default styling (gray/neutral)
  let bgColor = "var(--bg-muted, #f3f4f6)";
  let textColor = "var(--text-secondary, #4b5563)";

  // Check if children is a string to auto-apply semantic colors
  // since Scheduled.jsx uses <Badge>{payment.status}</Badge>
  const statusText = typeof children === "string" ? children.toUpperCase() : "";

  if (variant === "success" || statusText === "ACTIVE") {
    bgColor = "var(--bg-success-light, #dcfce7)";
    textColor = "var(--text-success, #166534)";
  } else if (
    variant === "danger" ||
    statusText === "CANCELLED" ||
    statusText === "INACTIVE"
  ) {
    bgColor = "var(--bg-danger-light, #fee2e2)";
    textColor = "var(--text-danger, #991b1b)";
  } else if (variant === "warning" || statusText === "PENDING") {
    bgColor = "var(--bg-warning-light, #fef3c7)";
    textColor = "var(--text-warning, #92400e)";
  }

  return (
    <span
      className={`badge ${className}`.trim()}
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: "0.25rem 0.6rem",
        borderRadius: "9999px", // Pill shape
        fontSize: "0.75rem",
        fontWeight: "600",
        letterSpacing: "0.025em",
        textTransform: "uppercase",
        backgroundColor: bgColor,
        color: textColor,
        whiteSpace: "nowrap",
      }}
      {...props}
    >
      {children}
    </span>
  );
}
