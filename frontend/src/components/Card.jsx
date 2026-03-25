import React from "react";

/**
 * CardHeader Component
 * Used for the top section of a Card, typically containing a title or actions.
 */
export function CardHeader({ children, className = "", ...props }) {
  return (
    <div
      className={`card-header ${className}`.trim()}
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: "1rem",
      }}
      {...props}
    >
      {children}
    </div>
  );
}

/**
 * Main Card Component
 * A flexible container for wrapping content with standard banking ledger styling.
 */
export default function Card({ children, className = "", ...props }) {
  return (
    <div
      className={`card ${className}`.trim()}
      style={{
        backgroundColor: "var(--bg-surface, #ffffff)",
        borderRadius: "var(--radius-md, 8px)",
        boxShadow: "var(--shadow-sm, 0 1px 3px rgba(0,0,0,0.1))",
        padding: "var(--spacing-md, 1.5rem)",
        border: "1px solid var(--border-color, #e5e7eb)",
        // If they pass an onClick, give it a pointer cursor
        cursor: props.onClick ? "pointer" : "default",
      }}
      {...props}
    >
      {children}
    </div>
  );
}
