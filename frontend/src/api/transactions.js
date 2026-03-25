import api from "../axios";
import { v4 as uuidv4 } from "uuid";

export const transactionsApi = {
  transfer: (data) =>
    api.post("/transactions/transfer", {
      ...data,
      // Auto-generate idempotency key if not provided — prevents duplicates on retry
      idempotencyKey: data.idempotencyKey || uuidv4(),
    }),

  getById: (id) => api.get(`/transactions/${id}`),

  getByAccount: (accountNumber, page = 0, size = 20) =>
    api.get(`/accounts/${accountNumber}/transactions`, {
      params: { page, size, sort: "createdAt,desc" },
    }),

  getStatement: (accountNumber, page = 0, size = 20) =>
    api.get(`/accounts/${accountNumber}/statement`, {
      params: { page, size, sort: "createdAt,desc" },
    }),

  deposit: (data) => api.post("/admin/transactions/deposit", data),
};
