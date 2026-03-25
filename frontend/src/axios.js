import axios from "axios";
import toast from "react-hot-toast";

/**
 * Configured axios instance.
 *
 * Handles:
 *  1. Base URL from env variable
 *  2. Attach JWT Bearer token on every request from localStorage
 *  3. On 401 → attempt silent token refresh, retry original request once
 *  4. On 403 → show toast "Access denied"
 *  5. On network error → show toast
 *  6. On 422 validation errors → show the field errors from RFC 7807 body
 *
 * Import this (not raw axios) everywhere in the app.
 */

const api = axios.create({
  baseURL: "/api/v1", // Vite proxy forwards to VITE_API_BASE_URL
  timeout: 15000,
  headers: {
    "Content-Type": "application/json",
  },
});

// ── Request interceptor — attach token ──────────────────────────────────────
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// ── Response interceptor — handle errors + token refresh ────────────────────
let isRefreshing = false;
let failedQueue = []; // requests that came in while refresh was in-flight

const processQueue = (error, token = null) => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve(token);
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,

  async (error) => {
    const originalRequest = error.config;

    // ── 401 Unauthorized — try refresh ──────────────────────────────────────
    if (error.response?.status === 401 && !originalRequest._retry) {
      // Don't retry refresh calls themselves — would infinite loop
      if (
        originalRequest.url?.includes("/auth/refresh") ||
        originalRequest.url?.includes("/auth/login")
      ) {
        clearAuthAndRedirect();
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // Queue this request until refresh completes
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = localStorage.getItem("refreshToken");
        if (!refreshToken) throw new Error("No refresh token");

        const { data } = await axios.post("/api/v1/auth/refresh", {
          refreshToken,
        });
        const newToken = data.accessToken;

        localStorage.setItem("accessToken", newToken);
        if (data.refreshToken)
          localStorage.setItem("refreshToken", data.refreshToken);

        api.defaults.headers.common.Authorization = `Bearer ${newToken}`;
        originalRequest.headers.Authorization = `Bearer ${newToken}`;

        processQueue(null, newToken);
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearAuthAndRedirect();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // ── 403 Forbidden ────────────────────────────────────────────────────────
    if (error.response?.status === 403) {
      toast.error("Access denied — you don't have permission for this action.");
      return Promise.reject(error);
    }

    // ── 422 Validation errors — show field errors ────────────────────────────
    if (error.response?.status === 422) {
      const data = error.response.data;
      if (data?.fieldErrors) {
        const messages = Object.entries(data.fieldErrors)
          .map(([field, errors]) => `${field}: ${errors[0]}`)
          .join("\n");
        toast.error(messages, { duration: 5000 });
      } else if (data?.detail) {
        toast.error(data.detail);
      }
      return Promise.reject(error);
    }

    // ── 409 Conflict ────────────────────────────────────────────────────────
    if (error.response?.status === 409) {
      const detail =
        error.response.data?.detail || "Conflict — resource already exists.";
      toast.error(detail);
      return Promise.reject(error);
    }

    // ── 503 Service Unavailable (lock timeout) ───────────────────────────────
    if (error.response?.status === 503) {
      toast.error("Server is busy — please try again in a moment.", {
        duration: 4000,
      });
      return Promise.reject(error);
    }

    // ── 500 Internal Server Error ────────────────────────────────────────────
    if (error.response?.status >= 500) {
      toast.error("Server error — please try again later.");
      return Promise.reject(error);
    }

    // ── Network error (no response) ──────────────────────────────────────────
    if (!error.response) {
      toast.error("Cannot reach the server — check your connection.");
      return Promise.reject(error);
    }

    return Promise.reject(error);
  },
);

function clearAuthAndRedirect() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
  // Navigate to login — use window.location since we're outside React tree
  if (!window.location.pathname.includes("/login")) {
    window.location.href = "/login";
  }
}

export default api;
