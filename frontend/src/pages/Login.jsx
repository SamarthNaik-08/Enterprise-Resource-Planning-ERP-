import { Button, Card, Flex, Form, Input, Tag, Typography, App } from "antd";
import { LockOutlined, MailOutlined } from "@ant-design/icons";
import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getErrorMessage } from "../api/client";

const { Title, Text } = Typography;

const demoUsers = [
  { role: "ADMIN", email: "admin@erp.com", password: "admin123" },
  { role: "MANAGER", email: "manager@erp.com", password: "manager123" },
  { role: "EMPLOYEE", email: "employee@erp.com", password: "employee123" },
];

export default function Login() {
  const { login, user } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();

  if (user) {
    return <Navigate to="/" replace />;
  }

  const onFinish = async (values) => {
    try {
      await login(values.email, values.password);
      message.success("Welcome back!");
      navigate("/");
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "linear-gradient(135deg, #1e3a8a 0%, #2563eb 50%, #38bdf8 100%)",
        padding: 16,
      }}
    >
      <Card style={{ width: 420, boxShadow: "0 20px 60px rgba(0,0,0,.3)" }}>
        <Flex vertical gap={8} style={{ textAlign: "center", marginBottom: 20 }}>
          <Title level={3} style={{ margin: 0 }}>
            ERP System
          </Title>
          <Text type="secondary">Inventory · Purchasing · Sales</Text>
        </Flex>

        <Form layout="vertical" onFinish={onFinish} initialValues={{ email: "admin@erp.com", password: "admin123" }}>
          <Form.Item
            name="email"
            rules={[{ required: true, message: "Email is required" }, { type: "email" }]}
          >
            <Input prefix={<MailOutlined />} placeholder="Email" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: "Password is required" }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="Password" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block>
            Sign in
          </Button>
        </Form>

        <div style={{ marginTop: 24 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Demo accounts (click to fill):
          </Text>
          <Flex gap={8} style={{ marginTop: 8 }} wrap="wrap">
            {demoUsers.map((u) => (
              <Button
                key={u.email}
                size="small"
                onClick={() => onFinish({ email: u.email, password: u.password })}
              >
                <Tag color={u.role === "ADMIN" ? "red" : u.role === "MANAGER" ? "blue" : "green"} style={{ marginRight: 4 }}>
                  {u.role}
                </Tag>
                {u.email}
              </Button>
            ))}
          </Flex>
        </div>
      </Card>
    </div>
  );
}
