import api from "../axios";

export const scheduledApi = {
  getAll: () => api.get("/scheduled-payments"),
  create: (data) => api.post("/scheduled-payments", data),
  cancel: (id) => api.delete(`/scheduled-payments/${id}`),
};
