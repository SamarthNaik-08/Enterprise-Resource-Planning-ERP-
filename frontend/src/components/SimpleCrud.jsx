import { useEffect, useState } from "react";
import { Button, Card, Flex, Form, Modal, Popconfirm, Space, Table, App } from "antd";
import { DownloadOutlined, PlusOutlined } from "@ant-design/icons";
import client, { getErrorMessage } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { exportToCsv } from "../utils/exportUtils";

/**
 * Reusable CRUD page for simple entities (suppliers / customers).
 * `formItems` is JSX with the form fields; field names must match the API body.
 */
export default function SimpleCrud({ title, endpoint, columns, formItems }) {
  const { message } = App.useApp();
  const { canWrite } = useAuth();
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await client.get(endpoint);
      setData(data);
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [endpoint]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  const openEdit = (record) => {
    setEditing(record);
    form.setFieldsValue(record);
    setOpen(true);
  };

  const onFinish = async (values) => {
    try {
      if (editing) {
        await client.put(`${endpoint}/${editing.id}`, values);
        message.success(`${title} updated`);
      } else {
        await client.post(endpoint, values);
        message.success(`${title} created`);
      }
      setOpen(false);
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const onDelete = async (id) => {
    try {
      await client.delete(`${endpoint}/${id}`);
      message.success(`${title} deleted`);
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const actionColumn = {
    title: "Actions",
    key: "actions",
    width: 160,
    render: (_, record) =>
      canWrite && (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            Edit
          </Button>
          <Popconfirm
            title={`Delete this ${title.toLowerCase()}?`}
            onConfirm={() => onDelete(record.id)}
          >
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
  };

  return (
    <Card
      title={title}
      extra={
        <Space>
          <Button
            icon={<DownloadOutlined />}
            onClick={() =>
              exportToCsv(
                `${title.toLowerCase()}s.csv`,
                columns.map((c) => ({ label: c.title, key: c.dataIndex })),
                data
              )
            }
          >
            Export CSV
          </Button>
          {canWrite && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              New
            </Button>
          )}
        </Space>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={[...columns, actionColumn]}
        dataSource={data}
        pagination={{ pageSize: 10, showSizeChanger: false }}
      />

      <Modal
        title={editing ? `Edit ${title}` : `New ${title}`}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Flex vertical gap={12}>
          <Form form={form} layout="vertical" onFinish={onFinish}>
            {formItems}
          </Form>
        </Flex>
      </Modal>
    </Card>
  );
}
