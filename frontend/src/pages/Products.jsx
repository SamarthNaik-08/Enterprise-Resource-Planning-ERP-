import { useEffect, useState } from "react";
import {
  App,
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tooltip,
} from "antd";
import { DownloadOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from "@ant-design/icons";
import client, { getErrorMessage } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { formatMoney } from "../utils/format";
import { exportToCsv } from "../utils/exportUtils";

export default function Products() {
  const { message } = App.useApp();
  const { canWrite } = useAuth();
  const [form] = Form.useForm();

  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [suppliers, setSuppliers] = useState([]);

  const load = async (page = 1, size = 10, q = search) => {
    setLoading(true);
    try {
      const { data: res } = await client.get("/products", {
        params: { q: q || undefined, page: page - 1, size },
      });
      setData(res.content);
      setPagination({ current: res.number + 1, pageSize: res.size, total: res.totalElements });
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1, 10, "");
    client
      .get("/suppliers")
      .then((res) =>
        setSuppliers(res.data.map((s) => ({ value: s.id, label: s.name })))
      )
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ quantityInStock: 0, reorderLevel: 10 });
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
        await client.put(`/products/${editing.id}`, values);
        message.success("Product updated");
      } else {
        await client.post("/products", values);
        message.success("Product created");
      }
      setOpen(false);
      load(pagination.current, pagination.pageSize);
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const onDelete = async (id) => {
    try {
      await client.delete(`/products/${id}`);
      message.success("Product deleted");
      load(pagination.current, pagination.pageSize);
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const columns = [
    { title: "SKU", dataIndex: "sku", width: 130 },
    { title: "Name", dataIndex: "name" },
    { title: "Category", dataIndex: "category", width: 130 },
    {
      title: "Unit Price",
      dataIndex: "unitPrice",
      width: 120,
      align: "right",
      render: formatMoney,
    },
    {
      title: "Stock",
      dataIndex: "quantityInStock",
      width: 110,
      render: (v, r) =>
        v <= r.reorderLevel ? (
          <Tooltip title="At or below reorder level">
            <span style={{ color: "#dc2626", fontWeight: 600 }}>{v} ⚠</span>
          </Tooltip>
        ) : (
          v
        ),
    },
    { title: "Reorder at", dataIndex: "reorderLevel", width: 110 },
    { title: "Supplier", dataIndex: "supplierName", render: (v) => v || "-" },
  ];

  if (canWrite) {
    columns.push({
      title: "Actions",
      width: 170,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>
            Edit
          </Button>
          <Popconfirm title="Delete this product?" onConfirm={() => onDelete(record.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    });
  }

  return (
    <Card
      title="Products"
      extra={
        <Space>
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="Search name, SKU, category"
            style={{ width: 240 }}
            onChange={(e) => {
              setSearch(e.target.value);
              load(1, pagination.pageSize, e.target.value);
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => load()} />
          <Button
            icon={<DownloadOutlined />}
            onClick={() =>
              exportToCsv(
                "products_catalog.csv",
                [
                  { label: "SKU", key: "sku" },
                  { label: "Name", key: "name" },
                  { label: "Category", key: "category" },
                  { label: "Unit Price ($)", key: "unitPrice" },
                  { label: "In Stock", key: "quantityInStock" },
                  { label: "Reorder Level", key: "reorderLevel" },
                  { label: "Supplier", key: "supplierName" },
                ],
                data
              )
            }
          >
            Export CSV
          </Button>
          {canWrite && (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              New Product
            </Button>
          )}
        </Space>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{ ...pagination, showSizeChanger: false }}
        onChange={(p) => load(p.current, p.pageSize)}
      />

      <Modal
        title={editing ? "Edit Product" : "New Product"}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
        width={560}
      >
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="sku"
                label="SKU"
                rules={[{ required: true, message: "SKU is required" }]}
              >
                <Input placeholder="e.g. TP-LAP-001" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label="Name"
                rules={[{ required: true, message: "Name is required" }]}
              >
                <Input placeholder="Product name" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="category" label="Category">
                <Input placeholder="e.g. Laptops" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="supplierId"
                label="Supplier"
              >
                <Select allowClear placeholder="Select supplier" options={suppliers} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="unitPrice"
                label="Unit Price ($)"
                rules={[{ required: true, message: "Price is required" }]}
              >
                <InputNumber min={0} step={0.01} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="quantityInStock"
                label="Quantity in Stock"
                rules={[{ required: true, message: "Quantity is required" }]}
              >
                <InputNumber min={0} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                name="reorderLevel"
                label="Reorder Level"
                rules={[{ required: true, message: "Reorder level is required" }]}
              >
                <InputNumber min={0} style={{ width: "100%" }} />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </Card>
  );
}
