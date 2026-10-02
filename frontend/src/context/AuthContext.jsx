import { createContext, useContext, useMemo, useState } from "react";
import client from "../api/client";

const AuthContext = createContext(null);

function readStoredUser() {
  try {
    const raw = localStorage.getItem("erp_user");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser);

  const persistSession = (data) => {
    // Tokens live in dedicated keys; the user object carries profile info only
    localStorage.setItem("erp_token", data.accessToken);
    localStorage.setItem("erp_refresh", data.refreshToken);
    const profile = {
      id: data.id,
      fullName: data.fullName,
      email: data.email,
      role: data.role,
    };
    localStorage.setItem("erp_user", JSON.stringify(profile));
    setUser(profile);
    return profile;
  };

  const login = async (email, password) => {
    const { data } = await client.post("/auth/login", { email, password });
    return persistSession(data);
  };

  const logout = async () => {
    // Best-effort server-side revocation of the refresh token (real logout)
    const refreshToken = localStorage.getItem("erp_refresh");
    try {
      if (refreshToken) {
        await client.post("/auth/logout", { refreshToken });
      }
    } catch {
      // session is being cleared anyway
    }
    localStorage.removeItem("erp_token");
    localStorage.removeItem("erp_refresh");
    localStorage.removeItem("erp_user");
    setUser(null);
  };

  const value = useMemo(
    () => ({
      user,
      login,
      logout,
      // Role helpers mirroring the backend RBAC rules
      canWrite: !!user && user.role !== "EMPLOYEE",
      isAdmin: !!user && user.role === "ADMIN",
    }),
    [user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
