import React, { useEffect } from "react";

export default function Modal({
  isOpen,
  onClose,
  title,
  size = "md",
  children,
}) {
  // Prevent background scrolling when modal is open
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "unset";
    }
    return () => {
      document.body.style.overflow = "unset";
    };
  }, [isOpen]);

  if (!isOpen) return null;

  // Determine max-width based on the size prop
  const sizeMap = {
    sm: "400px",
    md: "600px",
    lg: "800px",
  };

  return (
    <div
      className="modal-backdrop fade-in"
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 50,
        padding: "1rem",
      }}
      onClick={onClose} // Close when clicking the backdrop
    >
      <div
        className="modal-content"
        style={{
          backgroundColor: "var(--bg-surface, #ffffff)",
          borderRadius: "var(--radius-lg, 12px)",
          width: "100%",
          maxWidth: sizeMap[size] || sizeMap.md,
          maxHeight: "90vh",
          overflowY: "auto",
          boxShadow: "var(--shadow-xl, 0 20px 25px -5px rgba(0,0,0,0.1))",
        }}
        onClick={(e) => e.stopPropagation()} // Prevent clicks inside modal from closing it
      >
        {/* Modal Header */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "1.25rem 1.5rem",
            borderBottom: "1px solid var(--border-color, #e5e7eb)",
          }}
        >
          <h2 style={{ margin: 0, fontSize: "1.25rem", fontWeight: "600" }}>
            {title}
          </h2>
          <button
            onClick={onClose}
            style={{
              background: "none",
              border: "none",
              fontSize: "1.5rem",
              cursor: "pointer",
              color: "var(--text-secondary, #6b7280)",
              lineHeight: 1,
            }}
            aria-label="Close modal"
          >
            &times;
          </button>
        </div>

        {/* Modal Body */}
        <div style={{ padding: "1.5rem" }}>{children}</div>
      </div>
    </div>
  );
}
