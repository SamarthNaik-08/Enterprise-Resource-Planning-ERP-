import { useMemo, useState } from "react";
import { Avatar, Dropdown, Layout, Menu, Tag, Typography } from "antd";
import {
  ContactsOutlined,
  DashboardOutlined,
  HistoryOutlined,
  InboxOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ShoppingOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const { Sider, Header, Content } = Layout;
const { Text } = Typography;

const roleColors = { ADMIN: "red", MANAGER: "blue", EMPLOYEE: "green" };

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout, isAdmin, canWrite } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = useMemo(() => {
    const items = [
      { key: "/", icon: <DashboardOutlined />, label: "Dashboard" },
      { key: "/products", icon: <InboxOutlined />, label: "Products" },
      { key: "/purchase-orders", icon: <ShoppingCartOutlined />, label: "Purchase Orders" },
      { key: "/sales-orders", icon: <ShoppingOutlined />, label: "Sales Orders" },
      { key: "/suppliers", icon: <TeamOutlined />, label: "Suppliers" },
      { key: "/customers", icon: <ContactsOutlined />, label: "Customers" },
    ];
    if (canWrite) {
      items.push({ key: "/audit-logs", icon: <HistoryOutlined />, label: "Audit Trail" });
    }
    if (isAdmin) {
      items.push({ type: "divider" });
      items.push({ key: "/users", icon: <UserOutlined />, label: "Users" });
    }
    return items;
  }, [isAdmin, canWrite]);

  const userMenu = {
    items: [
      { key: "role", label: <Tag color={roleColors[user?.role]}>{user?.role}</Tag>, disabled: true },
      { type: "divider" },
      {
        key: "logout",
        icon: <LogoutOutlined />,
        label: "Sign out",
        onClick: () => {
          logout();
          navigate("/login");
        },
      },
    ],
  };

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider trigger={null} collapsible collapsed={collapsed} width={230}>
        <div
          style={{
            height: 64,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            gap: 8,
            color: "#fff",
          }}
        >
          <InboxOutlined style={{ fontSize: 22, color: "#60a5fa" }} />
          {!collapsed && (
            <Text strong style={{ color: "#fff", fontSize: 17, margin: 0 }}>
              ERP System
            </Text>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: "#fff",
            padding: "0 20px",
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            borderBottom: "1px solid #f0f0f0",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
            {collapsed ? (
              <MenuUnfoldOutlined
                style={{ fontSize: 17 }}
                onClick={() => setCollapsed(false)}
              />
            ) : (
              <MenuFoldOutlined
                style={{ fontSize: 17 }}
                onClick={() => setCollapsed(true)}
              />
            )}
            <Text type="secondary">Enterprise Resource Planning</Text>
          </div>
          <Dropdown menu={userMenu} placement="bottomRight">
            <div style={{ cursor: "pointer", display: "flex", alignItems: "center", gap: 10 }}>
              <Avatar style={{ backgroundColor: "#2563eb" }} icon={<UserOutlined />} />
              <div style={{ lineHeight: 1.2 }}>
                <div style={{ fontWeight: 600 }}>{user?.fullName}</div>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {user?.email}
                </Text>
              </div>
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: 20 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
