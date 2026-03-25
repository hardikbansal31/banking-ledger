import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
} from "react-router-dom";
import { Toaster } from "react-hot-toast";
import { useAuthStore } from "./store/authStore";
import Sidebar from "./components/Sidebar";
import Login from "./pages/Login";
// import Dashboard from "./pages/Dashboard";
// import Accounts from "./pages/Accounts";
// import Transfer from "./pages/Transfer";
// import Statement from "./pages/Statement";
// import Scheduled from "./pages/Scheduled";

/**
 * ProtectedLayout — wraps all authenticated pages.
 * Redirects to /login if no access token exists.
 * Renders the Sidebar + main content area.
 */
function ProtectedLayout() {
  const { accessToken } = useAuthStore();

  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="app-layout">
      <Sidebar />
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      {/* Toast container — positioned top-right, uses our font */}
      <Toaster
        position="top-right"
        gutter={10}
        toastOptions={{
          duration: 3500,
          style: {
            fontFamily: "var(--font-body)",
            fontSize: "0.9rem",
            borderRadius: "var(--radius-md)",
            boxShadow: "var(--shadow-md)",
            padding: "12px 16px",
            color: "var(--color-text-primary)",
            background: "var(--color-bg-card)",
            border: "1px solid var(--color-border)",
            maxWidth: "380px",
          },
          success: {
            iconTheme: {
              primary: "var(--color-credit)",
              secondary: "#fff",
            },
          },
          error: {
            duration: 5000,
            iconTheme: {
              primary: "var(--color-debit)",
              secondary: "#fff",
            },
          },
        }}
      />

      <Routes>
        {/* Public */}
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<Navigate to="/dashboard" replace />} />

        {/* Protected */}
        <Route element={<ProtectedLayout />}>
          {/* <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/accounts" element={<Accounts />} />
          <Route path="/transfer" element={<Transfer />} />
          <Route path="/statement" element={<Statement />} />
          <Route path="/scheduled" element={<Scheduled />} /> */}
        </Route>

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
