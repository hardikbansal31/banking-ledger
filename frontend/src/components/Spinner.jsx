import React from "react";

/**
 * PageSpinner Component
 * A full-height centered loading spinner for page transitions or heavy data fetches.
 */
export function PageSpinner() {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        minHeight: "50vh", // Takes up a good chunk of the page
        width: "100%",
      }}
    >
      <div
        className="spinner"
        style={{
          width: "40px",
          height: "40px",
          border: "4px solid var(--border-color, #e5e7eb)",
          borderTopColor: "var(--color-primary, #3b82f6)",
          borderRadius: "50%",
          animation: "spin 1s linear infinite",
        }}
      />
      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

/**
 * EmptyState Component
 * Used when lists (like active scheduled payments) return no results.
 */
export function EmptyState({ icon, title, description, action }) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "3rem 1.5rem",
        textAlign: "center",
      }}
    >
      {icon && (
        <div
          style={{
            fontSize: "3rem",
            color: "var(--text-muted, #9ca3af)",
            marginBottom: "1rem",
          }}
        >
          {icon}
        </div>
      )}
      <h3
        style={{
          margin: "0 0 0.5rem 0",
          fontSize: "1.125rem",
          color: "var(--text-primary, #111827)",
        }}
      >
        {title}
      </h3>
      <p
        style={{
          margin: "0 0 1.5rem 0",
          color: "var(--text-secondary, #4b5563)",
          maxWidth: "400px",
        }}
      >
        {description}
      </p>
      {action && <div>{action}</div>}
    </div>
  );
}
