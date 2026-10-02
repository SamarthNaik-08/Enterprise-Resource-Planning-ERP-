import { useEffect, useMemo, useState } from "react";
import {
  App,
  Button,
  Card,
  Form,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
} from "antd";
import { DownloadOutlined, MinusCircleOutlined, PlusOutlined, PrinterOutlined } from "@ant-design/icons";
import client, { getErrorMessage } from "../api/client";
import { useAuth } from "../context/AuthContext";
import { formatDateTime, formatMoney } from "../utils/format";
import { exportToCsv } from "../utils/exportUtils";
import StatusTag from "./StatusTag";
import InvoiceModal from "./InvoiceModal";

/**
 * Shared page for Purchase Orders and Sales Orders.
 *
 * props:
 *  - title         e.g. "Purchase Orders"
 *  - endpoint      e.g. "/purchase-orders"
 *  - partyLabel    "Supplier" | "Customer"
 *  - partyEndpoint "/suppliers" | "/customers"
 *  - fulfillLabel  "Receive" | "Complete"
 *  - fulfillUrl    "receive" | "complete"
 */
export default function OrderPage({
  title,
  endpoint,
  partyLabel,
  partyEndpoint,
  fulfillLabel,
  fulfillUrl,
}) {
  const { message } = App.useApp();
  const { canWrite } = useAuth();
  const [form] = Form.useForm();

  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [parties, setParties] = useState([]);
  const [products, setProducts] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [invoiceOrder, setInvoiceOrder] = useState(null);

  const handleExportCsv = () => {
    exportToCsv(
      `${title.toLowerCase().replace(/\s+/g, "_")}.csv`,
      [
        { label: "Order Number", key: "orderNumber" },
        { label: partyLabel, key: "partyName" },
        { label: "Status", key: "status" },
        { label: "Order Date", key: "orderDate", formatter: (d) => formatDateTime(d) },
        { label: "Total ($)", key: "total" },
      ],
      orders
    );
  };

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await client.get(endpoint);
      setOrders(data);
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    client
      .get(partyEndpoint)
      .then((res) => setParties(res.data.map((p) => ({ value: p.id, label: p.name }))))
      .catch(() => {});
    client
      .get("/products", { params: { size: 100 } })
      .then((res) =>
        setProducts(
          res.data.content.map((p) => ({
            value: p.id,
            label: `${p.name} (${p.sku})`,
            price: p.unitPrice,
          }))
        )
      )
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const watchedItems = Form.useWatch("items", form);

  const liveTotal = useMemo(() => {
    if (!watchedItems) return 0;
    return (watchedItems || []).reduce((sum, item) => {
      const product = products.find((p) => p.value === item?.productId);
      const qty = Number(item?.quantity) || 0;
      return sum + (product ? product.price * qty : 0);
    }, 0);
  }, [watchedItems, products]);

  const onFinish = async (values) => {
    setSubmitting(true);
    try {
      await client.post(endpoint, {
        partyId: values.partyId,
        items: values.items.map((i) => ({ productId: i.productId, quantity: i.quantity })),
      });
      message.success(`${title.replace(/s$/, "")} created — status DRAFT`);
      setOpen(false);
      form.resetFields();
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  const doAction = async (id, action, successText) => {
    try {
      await client.post(`${endpoint}/${id}/${action}`);
      message.success(successText);
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const onDelete = async (id) => {
    try {
      await client.delete(`${endpoint}/${id}`);
      message.success("Order deleted");
      load();
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  const itemColumns = [
    { title: "SKU", dataIndex: "sku", width: 130 },
    { title: "Product", dataIndex: "productName" },
    { title: "Qty", dataIndex: "quantity", width: 80 },
    {
      title: "Unit",
      dataIndex: "unitPrice",
      width: 110,
      align: "right",
      render: formatMoney,
    },
    {
      title: "Line total",
      dataIndex: "lineTotal",
      width: 120,
      align: "right",
      render: formatMoney,
    },
  ];

  const columns = [
    { title: "Order #", dataIndex: "orderNumber", width: 150 },
    { title: partyLabel, dataIndex: "partyName" },
    {
      title: "Status",
      dataIndex: "status",
      width: 120,
      render: (s) => <StatusTag status={s} />,
    },
    {
      title: "Date",
      dataIndex: "orderDate",
      width: 170,
      render: formatDateTime,
    },
    {
      title: "Total",
      dataIndex: "total",
      width: 120,
      align: "right",
      render: formatMoney,
    },
    {
      title: "Actions",
      key: "actions",
      width: 280,
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            icon={<PrinterOutlined />}
            onClick={() => setInvoiceOrder(record)}
          >
            {partyLabel === "Customer" ? "Invoice" : "PO Doc"}
          </Button>
          {canWrite && record.status === "DRAFT" && (
            <Popconfirm
              title={`${fulfillLabel} this order? Inventory will be updated.`}
              onConfirm={() =>
                doAction(record.id, fulfillUrl, `Order ${record.orderNumber} ${fulfillLabel.toLowerCase()}ed`)
              }
            >
              <Button size="small" type="primary">
                {fulfillLabel}
              </Button>
            </Popconfirm>
          )}
          {canWrite && record.status === "DRAFT" && (
            <Button
              size="small"
              onClick={() => doAction(record.id, "cancel", "Order cancelled")}
            >
              Cancel
            </Button>
          )}
          {canWrite && record.status === "DRAFT" && (
            <Popconfirm title="Delete this draft order?" onConfirm={() => onDelete(record.id)}>
              <Button size="small" danger>
                Delete
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={title}
      extra={
        <Space>
          <Button icon={<DownloadOutlined />} onClick={handleExportCsv}>
            Export CSV
          </Button>
          {canWrite && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                form.resetFields();
                setOpen(true);
              }}
            >
              New Order
            </Button>
          )}
        </Space>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={orders}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        expandable={{
          expandedRowRender: (record) => (
            <Table
              rowKey={(r) => r.productId}
              size="small"
              columns={itemColumns}
              dataSource={record.items}
              pagination={false}
              summary={() => (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={4}>
                    <strong>Total</strong>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={4} align="right">
                    <strong>{formatMoney(record.total)}</strong>
                  </Table.Summary.Cell>
                </Table.Summary.Row>
              )}
            />
          ),
        }}
      />

      <Modal
        title={`New ${title.replace(/s$/, "")}`}
        open={open}
        width={640}
        confirmLoading={submitting}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item
            name="partyId"
            label={partyLabel}
            rules={[{ required: true, message: `Please select a ${partyLabel.toLowerCase()}` }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={`Select ${partyLabel.toLowerCase()}`}
              options={parties}
            />
          </Form.Item>

          <Form.Item label="Line Items" required style={{ marginBottom: 8 }}>
            <Form.List
              name="items"
              initialValue={[{ productId: undefined, quantity: 1 }]}
              rules={[
                {
                  validator: async (_, items) => {
                    if (!items || items.length < 1) {
                      return Promise.reject(new Error("Add at least one line item"));
                    }
                    const productIds = items.map((i) => i?.productId).filter(Boolean);
                    if (new Set(productIds).size !== productIds.length) {
                      return Promise.reject(new Error("Duplicate products selected. Please merge quantities into a single line item."));
                    }
                  },
                },
              ]}
            >
              {(fields, { add, remove }, { errors }) => (
                <>
                  {fields.map(({ key, name, ...restField }) => (
                    <Space key={key} align="baseline" style={{ display: "flex", marginBottom: 4 }}>
                      <Form.Item
                        {...restField}
                        name={[name, "productId"]}
                        rules={[{ required: true, message: "Pick a product" }]}
                        style={{ minWidth: 340, marginBottom: 8 }}
                      >
                        <Select
                          showSearch
                          optionFilterProp="label"
                          placeholder="Product"
                          options={products}
                        />
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, "quantity"]}
                        rules={[{ required: true, message: "Qty" }]}
                        style={{ marginBottom: 8 }}
                      >
                        <InputNumber min={1} placeholder="Qty" style={{ width: 90 }} />
                      </Form.Item>
                      {fields.length > 1 && (
                        <MinusCircleOutlined onClick={() => remove(name)} />
                      )}
                    </Space>
                  ))}
                  <Form.Item style={{ marginBottom: 8 }}>
                    <Button
                      type="dashed"
                      onClick={() => add()}
                      block
                      icon={<PlusOutlined />}
                    >
                      Add line item
                    </Button>
                    <Form.ErrorList errors={errors} />
                  </Form.Item>
                </>
              )}
            </Form.List>
          </Form.Item>

          <div style={{ textAlign: "right", fontSize: 16 }}>
            Estimated total: <strong>{formatMoney(liveTotal)}</strong>
          </div>
        </Form>
      </Modal>

      <InvoiceModal
        open={!!invoiceOrder}
        onClose={() => setInvoiceOrder(null)}
        order={invoiceOrder}
        type={partyLabel === "Customer" ? "Sales" : "Purchase"}
      />
    </Card>
  );
}
