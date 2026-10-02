import { Button, Divider, Modal, Table, Typography } from "antd";
import { PrinterOutlined } from "@ant-design/icons";
import { formatDateTime, formatMoney } from "../utils/format";
import StatusTag from "./StatusTag";

const { Title, Text } = Typography;

export default function InvoiceModal({ open, onClose, order, type = "Sales" }) {
  if (!order) return null;

  const isSales = type === "Sales";
  const docTitle = isSales ? "TAX INVOICE" : "PURCHASE ORDER";

  const handlePrint = () => {
    window.print();
  };

  const columns = [
    {
      title: "#",
      key: "index",
      width: 50,
      render: (_, __, index) => index + 1,
    },
    {
      title: "Item / Description",
      dataIndex: "productName",
      key: "productName",
      render: (text, record) => (
        <div>
          <Text strong>{text}</Text>
          <div style={{ fontSize: 12, color: "#8c8c8c" }}>SKU: {record.sku}</div>
        </div>
      ),
    },
    {
      title: "Quantity",
      dataIndex: "quantity",
      key: "quantity",
      align: "center",
      width: 100,
    },
    {
      title: isSales ? "Unit Price" : "Unit Cost",
      dataIndex: "unitPrice",
      key: "unitPrice",
      align: "right",
      width: 130,
      render: (val) => formatMoney(val),
    },
    {
      title: "Line Total",
      dataIndex: "lineTotal",
      key: "lineTotal",
      align: "right",
      width: 140,
      render: (val) => <Text strong>{formatMoney(val)}</Text>,
    },
  ];

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={750}
      footer={[
        <Button key="close" onClick={onClose}>
          Close
        </Button>,
        <Button key="print" type="primary" icon={<PrinterOutlined />} onClick={handlePrint}>
          Print / Save PDF
        </Button>,
      ]}
      style={{ top: 20 }}
    >
      <div id="printable-invoice" style={{ padding: "16px 12px" }}>
        {/* Document Header */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ fontSize: 26 }}>🏭</span>
              <Title level={3} style={{ margin: 0, color: "#1e3a8a" }}>
                Enterprise ERP Corp
              </Title>
            </div>
            <Text type="secondary" style={{ display: "block", marginTop: 4 }}>
              100 Innovation Parkway, Suite 400
              <br />
              San Francisco, CA 94105 · tax@erp-system.io
            </Text>
          </div>
          <div style={{ textAlign: "right" }}>
            <Title level={4} style={{ margin: 0, color: "#2563eb", letterSpacing: 1 }}>
              {docTitle}
            </Title>
            <Text strong style={{ fontSize: 16 }}>
              {order.orderNumber}
            </Text>
            <div style={{ marginTop: 6 }}>
              <StatusTag status={order.status} />
            </div>
          </div>
        </div>

        <Divider style={{ margin: "16px 0" }} />

        {/* Party and Order Details */}
        <div style={{ display: "flex", justifyContent: "space-between", gap: 24 }}>
          <div style={{ flex: 1 }}>
            <Text type="secondary" style={{ fontSize: 12, textTransform: "uppercase" }}>
              {isSales ? "Billed To (Customer):" : "Vendor / Supplier:"}
            </Text>
            <div style={{ fontSize: 16, fontWeight: 600, marginTop: 4 }}>{order.partyName}</div>
            <Text type="secondary">Client ID: #{order.partyId}</Text>
          </div>
          <div style={{ textAlign: "right" }}>
            <Text type="secondary" style={{ fontSize: 12, textTransform: "uppercase" }}>
              Order Date:
            </Text>
            <div style={{ fontWeight: 600, marginTop: 4 }}>{formatDateTime(order.orderDate)}</div>
            <Text type="secondary" style={{ fontSize: 12, marginTop: 4, display: "block" }}>
              Payment Terms: Net 30 Days
            </Text>
          </div>
        </div>

        {/* Itemized Table */}
        <div style={{ marginTop: 20 }}>
          <Table
            rowKey="productId"
            size="small"
            columns={columns}
            dataSource={order.items || []}
            pagination={false}
            bordered
          />
        </div>

        {/* Summary Totals */}
        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
          <div style={{ width: 280 }}>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "4px 0" }}>
              <Text type="secondary">Subtotal:</Text>
              <Text strong>{formatMoney(order.total)}</Text>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "4px 0" }}>
              <Text type="secondary">Tax (0% Standard):</Text>
              <Text>$0.00</Text>
            </div>
            <Divider style={{ margin: "8px 0" }} />
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                padding: "6px 0",
                fontSize: 18,
              }}
            >
              <Text strong>Grand Total:</Text>
              <Text strong style={{ color: "#2563eb" }}>
                {formatMoney(order.total)}
              </Text>
            </div>
          </div>
        </div>

        {/* Footer notes */}
        <div
          style={{
            marginTop: 30,
            padding: "12px 16px",
            background: "#f8fafc",
            borderRadius: 6,
            border: "1px dashed #cbd5e1",
            textAlign: "center",
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            Thank you for your business. For questions concerning this {isSales ? "invoice" : "purchase order"}, please contact billing@erp-system.io.
          </Text>
        </div>
      </div>
    </Modal>
  );
}
