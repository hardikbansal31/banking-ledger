import { create } from "zustand";
import { accountsApi } from "../api/accounts";
import toast from "react-hot-toast";

export const useAccountStore = create((set, get) => ({
  accounts: [],
  isLoading: false,
  lastFetched: null,

  fetchAccounts: async (force = false) => {
    // Don't refetch if data is fresh (< 30s) unless forced
    const age = Date.now() - (get().lastFetched || 0);
    if (!force && age < 30_000 && get().accounts.length > 0) return;

    set({ isLoading: true });
    try {
      const { data } = await accountsApi.getAll();
      set({ accounts: data, lastFetched: Date.now() });
    } catch {
      // axios interceptor already shows toast
    } finally {
      set({ isLoading: false });
    }
  },

  openAccount: async (accountData) => {
    set({ isLoading: true });
    try {
      const { data } = await accountsApi.create(accountData);
      set((state) => ({ accounts: [...state.accounts, data] }));
      toast.success(`Account ${data.accountNumber} opened successfully!`);
      return { success: true, account: data };
    } catch {
      return { success: false };
    } finally {
      set({ isLoading: false });
    }
  },

  // Refresh a single account (after a transfer)
  refreshAccount: async (accountNumber) => {
    try {
      const { data } = await accountsApi.getOne(accountNumber);
      set((state) => ({
        accounts: state.accounts.map((a) =>
          a.accountNumber === accountNumber ? data : a,
        ),
      }));
    } catch {
      // silent
    }
  },

  totalBalance: (currency = "USD") => {
    return get()
      .accounts.filter((a) => a.currency === currency && a.status === "ACTIVE")
      .reduce((sum, a) => sum + parseFloat(a.balance), 0);
  },
}));
