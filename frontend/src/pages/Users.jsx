import { useEffect, useState } from "react";
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import client, { getErrorMessage } from "../api/client";
import { useAuth } from "../context/AuthContext";

const roleColors = { ADMIN: "red", MANAGER: "blue", EMPLOYEE: "green" };

export default function Users() {
  const { message } = App.useApp();
  const { user: currentUser } = useAuth();
  const [form] = Form.useForm();

  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await client.get("/users");
      setUsers(data);
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const onCreate = async (values) => {
    try {
      await client.post("/users", values);
      message.success("User created");
      setOpen(false);
      form.resetFields();
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const onRoleChange = async (id, role) => {
    try {
      await client.patch(`/users/${id}/role`, { role });
      message.success("Role updated");
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const onDelete = async (id) => {
    try {
      await client.delete(`/users/${id}`);
      message.success("User deleted");
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const columns = [
    { title: "Name", dataIndex: "fullName" },
    { title: "Email", dataIndex: "email" },
    {
      title: "Role",
      dataIndex: "role",
      width: 220,
      render: (role, record) =>
        record.email === currentUser?.email ? (
          <Tag color={roleColors[role]}>{role}</Tag>
        ) : (
          <Select
            size="small"
            value={role}
            style={{ width: 140 }}
            onChange={(r) => onRoleChange(record.id, r)}
            options={Object.keys(roleColors).map((r) => ({ value: r, label: r }))}
          />
        ),
    },
    {
      title: "Actions",
      width: 120,
      render: (_, record) =>
        record.email !== currentUser?.email && (
          <Popconfirm title="Delete this user?" onConfirm={() => onDelete(record.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        ),
    },
  ];

  return (
    <Card
      title="User Management"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          New User
        </Button>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={users}
        pagination={false}
      />

      <Modal
        title="New User"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onCreate}>
          <Form.Item
            name="fullName"
            label="Full Name"
            rules={[{ required: true, message: "Name is required" }]}
          >
            <Input placeholder="Jane Doe" />
          </Form.Item>
          <Form.Item
            name="email"
            label="Email"
            rules={[{ required: true, type: "email" }]}
          >
            <Input placeholder="jane@company.com" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: "Password is required" },
              { min: 6, message: "At least 6 characters" },
            ]}
          >
            <Input.Password placeholder="Minimum 6 characters" />
          </Form.Item>
          <Form.Item name="role" label="Role" rules={[{ required: true }]}>
            <Select
              placeholder="Select role"
              options={Object.keys(roleColors).map((r) => ({ value: r, label: r }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
