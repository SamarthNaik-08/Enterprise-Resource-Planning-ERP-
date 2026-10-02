import axios from "axios";

const client = axios.create({ baseURL: "/api" });

// Attach the short-lived access token to every request
client.interceptors.request.use((config) => {
  const token = localStorage.getItem("erp_token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Raw axios (no interceptors) used for the refresh call itself, to avoid loops
const bareAxios = axios.create({ baseURL: "/api" });

function clearSession() {
  localStorage.removeItem("erp_token");
  localStorage.removeItem("erp_refresh");
  localStorage.removeItem("erp_user");
  window.location.href = "/login";
}

let refreshing = null; // deduplicate concurrent refresh attempts

// On 401: try ONE silent refresh + retry; if that fails, hard logout
client.interceptors.response.use(
  (res) => res,
  async (err) => {
    const original = err.config;
    const status = err.response?.status;
    const isAuthEndpoint = original?.url?.includes("/auth/");

    if (status === 401 && original && !original._retry && !isAuthEndpoint) {
      original._retry = true;
      const refreshToken = localStorage.getItem("erp_refresh");

      if (refreshToken) {
        try {
          refreshing =
            refreshing ||
            bareAxios.post("/auth/refresh", { refreshToken }).then(({ data }) => {
              localStorage.setItem("erp_token", data.accessToken);
              localStorage.setItem("erp_refresh", data.refreshToken);
              return data;
            });
          const data = await refreshing;
          refreshing = null;
          original.headers.Authorization = `Bearer ${data.accessToken}`;
          return client(original); // replay the original request once
        } catch (refreshErr) {
          refreshing = null;
          clearSession();
          return Promise.reject(refreshErr);
        }
      }
      clearSession();
    }
    return Promise.reject(err);
  }
);

export function getErrorMessage(err) {
  return (
    err.response?.data?.message ||
    (err.response?.data?.fieldErrors &&
      Object.values(err.response.data.fieldErrors).join(", ")) ||
    err.message ||
    "Something went wrong"
  );
}

export default client;
