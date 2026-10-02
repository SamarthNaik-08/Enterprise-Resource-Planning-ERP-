import { ConfigProvider, App as AntApp } from "antd";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import AppLayout from "./components/AppLayout";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Products from "./pages/Products";
import Suppliers from "./pages/Suppliers";
import Customers from "./pages/Customers";
import PurchaseOrders from "./pages/PurchaseOrders";
import SalesOrders from "./pages/SalesOrders";
import Users from "./pages/Users";
import AuditLogs from "./pages/AuditLogs";

function Protected({ children }) {
  const { user } = useAuth();
  return user ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: "#2563eb",
          borderRadius: 6,
          fontSize: 14,
        },
      }}
    >
      <AntApp>
        <AuthProvider>
          <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route
                path="/"
                element={
                  <Protected>
                    <AppLayout />
                  </Protected>
                }
              >
                <Route index element={<Dashboard />} />
                <Route path="products" element={<Products />} />
                <Route path="suppliers" element={<Suppliers />} />
                <Route path="customers" element={<Customers />} />
                <Route path="purchase-orders" element={<PurchaseOrders />} />
                <Route path="sales-orders" element={<SalesOrders />} />
                <Route path="audit-logs" element={<AuditLogs />} />
                <Route path="users" element={<Users />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </AntApp>
    </ConfigProvider>
  );
}
