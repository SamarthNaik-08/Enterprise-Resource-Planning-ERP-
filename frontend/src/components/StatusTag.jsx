import { Tag } from "antd";

const colors = {
  DRAFT: "gold",
  COMPLETED: "green",
  CANCELLED: "default",
};

export default function StatusTag({ status }) {
  return <Tag color={colors[status] || "blue"}>{status}</Tag>;
}
