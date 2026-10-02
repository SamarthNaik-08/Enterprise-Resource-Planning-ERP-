import OrderPage from "../components/OrderPage";

export default function SalesOrders() {
  return (
    <OrderPage
      title="Sales Orders"
      endpoint="/sales-orders"
      partyLabel="Customer"
      partyEndpoint="/customers"
      fulfillLabel="Complete"
      fulfillUrl="complete"
    />
  );
}
