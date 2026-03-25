import api from "../axios";

export const accountsApi = {
  getAll: () => api.get("/accounts"),
  getOne: (number) => api.get(`/accounts/${number}`),
  create: (data) => api.post("/accounts", data),
  freeze: (number) => api.post(`/admin/accounts/${number}/freeze`),
  activate: (number) => api.post(`/admin/accounts/${number}/activate`),
  close: (number) => api.delete(`/admin/accounts/${number}`),
};
