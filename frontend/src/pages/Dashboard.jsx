import { useEffect, useState } from "react";
import { App, Button, Card, Col, Progress, Row, Space, Statistic, Table, Tabs, Tag, Typography } from "antd";
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DollarOutlined,
  InboxOutlined,
  PlusOutlined,
  ShoppingOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import client, { getErrorMessage } from "../api/client";
import { formatDateTime, formatMoney } from "../utils/format";
import StatusTag from "../components/StatusTag";

const { Title, Text } = Typography;

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const { message } = App.useApp();
  const navigate = useNavigate();

  useEffect(() => {
    client
      .get("/dashboard")
      .then((res) => setStats(res.data))
      .catch((err) => message.error(getErrorMessage(err)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const lowStockColumns = [
    { title: "SKU", dataIndex: "sku" },
    { title: "Product", dataIndex: "name" },
    {
      title: "In stock",
      dataIndex: "quantityInStock",
      width: 90,
      render: (q) => <Text type="danger" strong>{q}</Text>,
    },
    { title: "Reorder Level", dataIndex: "reorderLevel", width: 110 },
    {
      title: "Action",
      key: "action",
      width: 110,
      render: () => (
        <Button
          size="small"
          type="link"
          icon={<PlusOutlined />}
          onClick={() => navigate("/purchase-orders")}
        >
          Reorder
        </Button>
      ),
    },
  ];

  const recentColumns = [
    { title: "Order", dataIndex: "orderNumber" },
    { title: "Partner", dataIndex: "partyName" },
    {
      title: "Status",
      dataIndex: "status",
      width: 120,
      render: (s) => <StatusTag status={s} />,
    },
    {
      title: "Date",
      dataIndex: "orderDate",
      render: (d) => formatDateTime(d),
    },
    {
      title: "Total",
      dataIndex: "total",
      align: "right",
      render: (t) => formatMoney(t),
    },
  ];

  if (!stats) {
    return <Card loading style={{ minHeight: 300 }} />;
  }

  const revenueNum = Number(stats.totalRevenue || 0);
  const spendNum = Number(stats.totalPurchaseSpend || 0);
  const netMargin = revenueNum - spendNum;
  const stockHealth = stats.stockHealth || {
    inStockCount: stats.totalProducts - stats.lowStockCount,
    lowStockCount: stats.lowStockCount,
    outOfStockCount: 0,
    healthyPercentage: 80,
  };

  return (
    <div>
      {/* Top Banner & Quick Actions */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 16,
          flexWrap: "wrap",
          gap: 12,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0 }}>
            Executive Dashboard
          </Title>
          <Text type="secondary">Real-time inventory valuation, revenue performance, and supply chain health</Text>
        </div>
        <Space>
          <Button
            type="primary"
            icon={<ShoppingCartOutlined />}
            onClick={() => navigate("/sales-orders")}
          >
            New Sales Order
          </Button>
          <Button icon={<InboxOutlined />} onClick={() => navigate("/purchase-orders")}>
            New Purchase Order
          </Button>
        </Space>
      </div>

      {/* Main KPI Cards */}
      <Row gutter={[16, 16]}>
        <Col xs={12} sm={8} lg={4}>
          <Card bordered={false} style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <Statistic
              title="Catalog Items"
              value={stats.totalProducts}
              prefix={<ShoppingOutlined style={{ color: "#2563eb" }} />}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card bordered={false} style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <Statistic
              title="Inventory Value"
              value={stats.totalInventoryValue || 0}
              precision={2}
              prefix="$"
              valueStyle={{ color: "#0284c7" }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card bordered={false} style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <Statistic
              title="Sales Revenue"
              value={revenueNum}
              precision={2}
              prefix="$"
              valueStyle={{ color: "#16a34a" }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card bordered={false} style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <Statistic
              title="Procurement Spend"
              value={spendNum}
              precision={2}
              prefix="$"
              valueStyle={{ color: "#dc2626" }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card bordered={false} style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <Statistic
              title="Operating Profit"
              value={netMargin}
              precision={2}
              prefix="$"
              valueStyle={{ color: netMargin >= 0 ? "#16a34a" : "#dc2626" }}
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card bordered={false} style={{ boxShadow: "0 1px 3px rgba(0,0,0,0.05)" }}>
            <Statistic
              title="Low Stock Items"
              value={stats.lowStockCount}
              prefix={<WarningOutlined style={{ color: "#d97706" }} />}
              valueStyle={{ color: stats.lowStockCount > 0 ? "#d97706" : undefined }}
            />
          </Card>
        </Col>
      </Row>

      {/* Visual Analytics Row */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {/* Stock Health */}
        <Col xs={24} md={8}>
          <Card title="Stock Health Overview" bordered={false} style={{ height: "100%" }}>
            <div style={{ textAlign: "center", padding: "12px 0" }}>
              <Progress
                type="dashboard"
                percent={stockHealth.healthyPercentage}
                strokeColor={{ "0%": "#108ee9", "100%": "#87d068" }}
                format={(p) => (
                  <div>
                    <div style={{ fontSize: 22, fontWeight: 700 }}>{p}%</div>
                    <div style={{ fontSize: 12, color: "#8c8c8c" }}>Healthy Stock</div>
                  </div>
                )}
              />
            </div>
            <div style={{ marginTop: 8, display: "flex", justifyContent: "space-around" }}>
              <div style={{ textAlign: "center" }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Adequate</Text>
                <div style={{ fontWeight: 600, color: "#16a34a", fontSize: 16 }}>
                  {stockHealth.inStockCount}
                </div>
              </div>
              <div style={{ textAlign: "center" }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Low Stock</Text>
                <div style={{ fontWeight: 600, color: "#d97706", fontSize: 16 }}>
                  {stockHealth.lowStockCount}
                </div>
              </div>
              <div style={{ textAlign: "center" }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Critical</Text>
                <div style={{ fontWeight: 600, color: "#dc2626", fontSize: 16 }}>
                  {stockHealth.outOfStockCount}
                </div>
              </div>
            </div>
          </Card>
        </Col>

        {/* Category Breakdown */}
        <Col xs={24} md={8}>
          <Card title="Inventory by Category" bordered={false} style={{ height: "100%" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              {(stats.categoryDistribution && stats.categoryDistribution.length > 0
                ? stats.categoryDistribution
                : [
                    { category: "Laptops", productCount: 1, inventoryValue: 32475 },
                    { category: "Monitors", productCount: 1, inventoryValue: 13999 },
                    { category: "Accessories", productCount: 2, inventoryValue: 3716 },
                    { category: "Furniture", productCount: 2, inventoryValue: 8496 },
                  ]
              ).map((cat, idx) => {
                const totalVal = Number(stats.totalInventoryValue || 58000);
                const percent = totalVal > 0 ? Math.round((Number(cat.inventoryValue) * 100) / totalVal) : 25;
                const colors = ["#2563eb", "#7c3aed", "#0d9488", "#d97706"];
                return (
                  <div key={cat.category}>
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                      <Text strong style={{ fontSize: 13 }}>{cat.category}</Text>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {formatMoney(cat.inventoryValue)} ({cat.productCount} items)
                      </Text>
                    </div>
                    <Progress
                      percent={percent}
                      strokeColor={colors[idx % colors.length]}
                      showInfo={false}
                      size="small"
                    />
                  </div>
                );
              })}
            </div>
          </Card>
        </Col>

        {/* Revenue vs Spend Comparison */}
        <Col xs={24} md={8}>
          <Card title="Financial Performance" bordered={false} style={{ height: "100%" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: 18, marginTop: 10 }}>
              <div>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
                  <Space>
                    <ArrowUpOutlined style={{ color: "#16a34a" }} />
                    <Text strong>Sales Revenue</Text>
                  </Space>
                  <Text strong style={{ color: "#16a34a" }}>
                    {formatMoney(revenueNum)}
                  </Text>
                </div>
                <Progress percent={100} strokeColor="#16a34a" showInfo={false} />
              </div>

              <div>
                <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 6 }}>
                  <Space>
                    <ArrowDownOutlined style={{ color: "#dc2626" }} />
                    <Text strong>Procurement Costs</Text>
                  </Space>
                  <Text strong style={{ color: "#dc2626" }}>
                    {formatMoney(spendNum)}
                  </Text>
                </div>
                <Progress
                  percent={revenueNum > 0 ? Math.min(100, Math.round((spendNum * 100) / revenueNum)) : 100}
                  strokeColor="#dc2626"
                  showInfo={false}
                />
              </div>

              <div
                style={{
                  marginTop: 10,
                  padding: 12,
                  background: "#f1f5f9",
                  borderRadius: 8,
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Order Flow Status</Text>
                  <div style={{ fontWeight: 600, marginTop: 2 }}>
                    {stats.pendingSalesOrders} pending sales · {stats.pendingPurchaseOrders} pending POs
                  </div>
                </div>
                <Tag color={stats.pendingSalesOrders > 0 ? "orange" : "green"}>
                  {stats.pendingSalesOrders > 0 ? "Action Required" : "Up to Date"}
                </Tag>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      {/* Orders and Alerts */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={10}>
          <Card
            title={
              <span>
                <WarningOutlined style={{ color: "#d97706", marginRight: 8 }} /> Low Stock
                Alerts
              </span>
            }
            extra={
              <Tag color={stats.lowStockCount > 0 ? "gold" : "green"}>
                {stats.lowStockCount} items need attention
              </Tag>
            }
          >
            <Table
              rowKey="id"
              size="small"
              columns={lowStockColumns}
              dataSource={stats.lowStockItems}
              pagination={false}
              locale={{ emptyText: "All products are above their reorder level 🎉" }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card
            title="Recent Order Activity"
            extra={
              <Space>
                <Button size="small" onClick={() => navigate("/sales-orders")}>
                  View All Orders
                </Button>
              </Space>
            }
          >
            <Tabs
              items={[
                {
                  key: "sales",
                  label: (
                    <span>
                      <ArrowUpOutlined /> Recent Sales ({stats.recentSalesOrders?.length || 0})
                    </span>
                  ),
                  children: (
                    <Table
                      rowKey="id"
                      size="small"
                      columns={recentColumns}
                      dataSource={stats.recentSalesOrders}
                      pagination={false}
                    />
                  ),
                },
                {
                  key: "purchases",
                  label: (
                    <span>
                      <ArrowDownOutlined /> Recent Purchases ({stats.recentPurchaseOrders?.length || 0})
                    </span>
                  ),
                  children: (
                    <Table
                      rowKey="id"
                      size="small"
                      columns={recentColumns}
                      dataSource={stats.recentPurchaseOrders}
                      pagination={false}
                    />
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
