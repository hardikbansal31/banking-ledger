import { create } from "zustand";
import { authApi } from "../api/auth";
import toast from "react-hot-toast";

/**
 * authStore — global authentication state.
 *
 * Persisted to localStorage so the user stays logged in on refresh.
 * The axios interceptor reads tokens directly from localStorage so
 * it doesn't need to import this store (avoids circular deps).
 */
export const useAuthStore = create((set, get) => ({
  user: JSON.parse(localStorage.getItem("user") || "null"),
  accessToken: localStorage.getItem("accessToken") || null,
  refreshToken: localStorage.getItem("refreshToken") || null,
  isLoading: false,

  // ── Computed ──────────────────────────────────────────────────────────────
  isAuthenticated: () => !!get().accessToken,
  isAdmin: () => get().user?.role === "ADMIN",

  // ── Actions ───────────────────────────────────────────────────────────────

  login: async (credentials) => {
    set({ isLoading: true });
    try {
      const { data } = await authApi.login(credentials);
      get()._setSession(data);
      toast.success(`Welcome back, ${data.user.firstName}!`);
      return { success: true };
    } catch (err) {
      const detail = err.response?.data?.detail || "Login failed.";
      toast.error(detail);
      return { success: false, error: detail };
    } finally {
      set({ isLoading: false });
    }
  },

  register: async (userData) => {
    set({ isLoading: true });
    try {
      const { data } = await authApi.register(userData);
      get()._setSession(data);
      toast.success(`Account created! Welcome, ${data.user.firstName}.`);
      return { success: true };
    } catch (err) {
      // Field-level errors handled by axios interceptor
      // Only handle non-422 errors here
      if (err.response?.status !== 422 && err.response?.status !== 409) {
        toast.error("Registration failed. Please try again.");
      }
      return { success: false };
    } finally {
      set({ isLoading: false });
    }
  },

  logout: async () => {
    try {
      await authApi.logout();
    } catch {
      // Swallow — we clear the session regardless
    } finally {
      get()._clearSession();
      toast.success("Logged out successfully.");
    }
  },

  // ── Internal helpers ──────────────────────────────────────────────────────

  _setSession: ({ accessToken, refreshToken, user }) => {
    localStorage.setItem("accessToken", accessToken);
    localStorage.setItem("refreshToken", refreshToken);
    localStorage.setItem("user", JSON.stringify(user));
    set({ accessToken, refreshToken, user });
  },

  _clearSession: () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
    set({ accessToken: null, refreshToken: null, user: null });
  },
}));
