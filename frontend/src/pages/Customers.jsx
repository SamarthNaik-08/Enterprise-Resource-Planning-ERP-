import { Form, Input } from "antd";
import SimpleCrud from "../components/SimpleCrud";

const columns = [
  { title: "Name", dataIndex: "name" },
  { title: "Email", dataIndex: "email" },
  { title: "Phone", dataIndex: "phone" },
  { title: "Address", dataIndex: "address" },
];

const formItems = (
  <>
    <Form.Item
      name="name"
      label="Name"
      rules={[{ required: true, message: "Name is required" }]}
    >
      <Input placeholder="Customer name" />
    </Form.Item>
    <Form.Item name="email" label="Email" rules={[{ type: "email" }]}>
      <Input placeholder="email@company.com" />
    </Form.Item>
    <Form.Item name="phone" label="Phone">
      <Input placeholder="+1 555 0100" />
    </Form.Item>
    <Form.Item name="address" label="Address">
      <Input.TextArea rows={2} placeholder="Address" />
    </Form.Item>
  </>
);

export default function Customers() {
  return (
    <SimpleCrud title="Customer" endpoint="/customers" columns={columns} formItems={formItems} />
  );
}
