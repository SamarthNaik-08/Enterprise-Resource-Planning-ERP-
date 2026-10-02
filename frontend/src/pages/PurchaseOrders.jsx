import OrderPage from "../components/OrderPage";

export default function PurchaseOrders() {
  return (
    <OrderPage
      title="Purchase Orders"
      endpoint="/purchase-orders"
      partyLabel="Supplier"
      partyEndpoint="/suppliers"
      fulfillLabel="Receive"
      fulfillUrl="receive"
    />
  );
}
