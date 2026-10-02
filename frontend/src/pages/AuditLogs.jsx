import { useEffect, useState } from "react";
import { App, Button, Card, Col, Input, Row, Select, Space, Table, Tag, Typography } from "antd";
import {
  FileTextOutlined,
  HistoryOutlined,
  ReloadOutlined,
  SearchOutlined,
  UserOutlined,
} from "@ant-design/icons";
import client, { getErrorMessage } from "../api/client";
import { formatDateTime } from "../utils/format";
import { exportToCsv } from "../utils/exportUtils";

const { Title, Text } = Typography;

const actionColors = {
  CREATE: "green",
  UPDATE: "blue",
  DELETE: "red",
  STATUS_CHANGE: "purple",
};

const entityColors = {
  Product: "cyan",
  SalesOrder: "gold",
  PurchaseOrder: "orange",
  Customer: "geekblue",
  Supplier: "magenta",
  User: "volcano",
};

export default function AuditLogs() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);
  const [entityFilter, setEntityFilter] = useState(null);
  const [actionFilter, setActionFilter] = useState(null);
  const { message } = App.useApp();

  const fetchLogs = (p = page, ps = pageSize) => {
    setLoading(true);
    const params = {
      page: p - 1,
      size: ps,
    };
    if (entityFilter) params.entity = entityFilter;
    if (actionFilter) params.action = actionFilter;

    client
      .get("/audit-logs", { params })
      .then((res) => {
        setLogs(res.data.content || []);
        setTotal(res.data.totalElements || 0);
      })
      .catch((err) => message.error(getErrorMessage(err)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchLogs(1, pageSize);
    setPage(1);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entityFilter, actionFilter]);

  const handleExportCsv = () => {
    exportToCsv("audit_trail.csv", [
      { label: "ID", key: "id" },
      { label: "Timestamp", key: "timestamp", formatter: (d) => formatDateTime(d) },
      { label: "Action", key: "action" },
      { label: "Entity", key: "entityName" },
      { label: "Entity ID", key: "entityId" },
      { label: "Performed By", key: "performedBy" },
      { label: "Details", key: "details" },
    ], logs);
  };

  const columns = [
    {
      title: "Timestamp",
      dataIndex: "timestamp",
      key: "timestamp",
      width: 170,
      render: (d) => (
        <span style={{ fontSize: 13, color: "#475569" }}>
          <HistoryOutlined style={{ marginRight: 6, color: "#94a3b8" }} />
          {formatDateTime(d)}
        </span>
      ),
    },
    {
      title: "Action",
      dataIndex: "action",
      key: "action",
      width: 130,
      render: (action) => (
        <Tag color={actionColors[action] || "default"} style={{ fontWeight: 600, fontSize: 12 }}>
          {action}
        </Tag>
      ),
    },
    {
      title: "Entity",
      dataIndex: "entityName",
      key: "entityName",
      width: 140,
      render: (entity, record) => (
        <Space orientation="horizontal" size={4}>
          <Tag orientation="horizontal" orientationMargin={0} color={entityColors[entity] || "blue"}>
            {entity}
          </Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>
            #{record.entityId}
          </Text>
        </Space>
      ),
    },
    {
      title: "User / Actor",
      dataIndex: "performedBy",
      key: "performedBy",
      width: 180,
      render: (user) => (
        <Space size={6}>
          <UserOutlined style={{ color: "#64748b" }} />
          <Text strong style={{ fontSize: 13 }}>
            {user}
          </Text>
        </Space>
      ),
    },
    {
      title: "Audit Details",
      dataIndex: "details",
      key: "details",
      render: (details) => (
        <Text style={{ fontFamily: "monospace", fontSize: 13, color: "#1e293b" }}>
          {details}
        </Text>
      ),
    },
  ];

  return (
    <div>
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
            Enterprise Audit Trail
          </Title>
          <Text type="secondary">
            Immutable log of all catalog modifications, order placements, and workflow status transitions
          </Text>
        </div>
        <Space>
          <Button icon={<FileTextOutlined />} onClick={handleExportCsv}>
            Export Audit CSV
          </Button>
          <Button icon={<ReloadOutlined />} onClick={() => fetchLogs(page, pageSize)}>
            Refresh
          </Button>
        </Space>
      </div>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} sm={12} md={8}>
            <Text style={{ display: "block", marginBottom: 4, fontSize: 12 }} type="secondary">
              Filter by Entity
            </Text>
            <Select
              allowClear
              placeholder="All Entities"
              style={{ width: "100%" }}
              value={entityFilter}
              onChange={setEntityFilter}
              options={[
                { label: "Product", value: "Product" },
                { label: "Sales Order", value: "SalesOrder" },
                { label: "Purchase Order", value: "PurchaseOrder" },
              ]}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <Text style={{ display: "block", marginBottom: 4, fontSize: 12 }} type="secondary">
              Filter by Action
            </Text>
            <Select
              allowClear
              placeholder="All Actions"
              style={{ width: "100%" }}
              value={actionFilter}
              onChange={setActionFilter}
              options={[
                { label: "CREATE", value: "CREATE" },
                { label: "UPDATE", value: "UPDATE" },
                { label: "STATUS_CHANGE", value: "STATUS_CHANGE" },
                { label: "DELETE", value: "DELETE" },
              ]}
            />
          </Col>
          <Col xs={24} md={8} style={{ textAlign: "right", alignSelf: "flex-end" }}>
            <Text type="secondary" style={{ fontSize: 13 }}>
              Showing {logs.length} of {total} total events
            </Text>
          </Col>
        </Row>
      </Card>

      <Card bodyStyle={{ padding: 0 }}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={logs}
          loading={loading}
          pagination={{
            current: page,
            pageSize: pageSize,
            total: total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
              fetchLogs(p, ps);
            },
          }}
        />
      </Card>
    </div>
  );
}
