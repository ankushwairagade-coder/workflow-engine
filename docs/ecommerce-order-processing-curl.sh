#!/bin/bash

# E-commerce Order Processing Workflow
# This workflow processes orders: checks inventory, processes payment, creates shipping label, and sends confirmation

curl --location 'http://localhost:8080/api/workflows' \
--header 'Content-Type: application/json' \
--data-raw '{
  "name": "ecommerce-order-processing",
  "description": "E-commerce order processing workflow: inventory check, payment processing, shipping, and customer confirmation",
  "nodes": [
    {
      "key": "order-received",
      "type": "INPUT",
      "displayName": "Order Received",
      "config": {
        "defaults": {
          "orderId": "",
          "customerEmail": "ankush.wairagade@paytm.com",
          "productId": "",
          "quantity": 1,
          "amount": 0.0
        }
      }
    },
    {
      "key": "check-inventory",
      "type": "IF_ELSE",
      "displayName": "Check Inventory Availability",
      "config": {
        "condition": "{{quantity}} > 0"
      }
    },
    {
      "key": "reserve-inventory",
      "type": "HTTP",
      "displayName": "Reserve Inventory",
      "config": {
        "url": "https://api.example.com/inventory/reserve",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"productId\": \"{{productId}}\", \"quantity\": {{quantity}}, \"orderId\": \"{{orderId}}\"}"
      }
    },
    {
      "key": "process-payment",
      "type": "HTTP",
      "displayName": "Process Payment",
      "config": {
        "url": "https://api.example.com/payment/process",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"orderId\": \"{{orderId}}\", \"amount\": {{amount}}, \"customerEmail\": \"{{customerEmail}}\"}"
      }
    },
    {
      "key": "check-payment",
      "type": "IF_ELSE",
      "displayName": "Payment Successful?",
      "config": {
        "condition": "{{process-payment::status}} == 200"
      }
    },
    {
      "key": "create-shipping-label",
      "type": "HTTP",
      "displayName": "Create Shipping Label",
      "config": {
        "url": "https://api.example.com/shipping/create-label",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"orderId\": \"{{orderId}}\", \"customerEmail\": \"{{customerEmail}}\", \"productId\": \"{{productId}}\", \"quantity\": {{quantity}}}"
      }
    },
    {
      "key": "send-confirmation",
      "type": "EMAIL",
      "displayName": "Send Confirmation to Customer",
      "config": {
        "to": "{{customerEmail}}",
        "subject": "Order Confirmation - Order #{{orderId}}",
        "body": "Dear Customer,\n\nThank you for your order!\n\nOrder ID: {{orderId}}\nProduct ID: {{productId}}\nQuantity: {{quantity}}\nAmount: ${{amount}}\n\nYour order has been processed successfully and will be shipped soon.\n\nShipping Label: {{create-shipping-label::body}}\n\nThank you for shopping with us!\n\nBest regards,\nE-commerce Team",
        "from": "orders@yourcompany.com"
      }
    },
    {
      "key": "inventory-unavailable-output",
      "type": "OUTPUT",
      "displayName": "Inventory Unavailable",
      "config": {
        "fields": ["orderId", "productId", "quantity"]
      }
    },
    {
      "key": "payment-failed-output",
      "type": "OUTPUT",
      "displayName": "Payment Failed",
      "config": {
        "fields": ["orderId", "process-payment"]
      }
    },
    {
      "key": "order-complete-output",
      "type": "OUTPUT",
      "displayName": "Order Complete",
      "config": {
        "fields": ["orderId", "reserve-inventory", "process-payment", "create-shipping-label", "send-confirmation"]
      }
    }
  ],
  "edges": [
    {
      "sourceKey": "order-received",
      "targetKey": "check-inventory"
    },
    {
      "sourceKey": "check-inventory",
      "targetKey": "reserve-inventory",
      "conditionExpression": "true"
    },
    {
      "sourceKey": "check-inventory",
      "targetKey": "inventory-unavailable-output",
      "conditionExpression": "false"
    },
    {
      "sourceKey": "reserve-inventory",
      "targetKey": "process-payment"
    },
    {
      "sourceKey": "process-payment",
      "targetKey": "check-payment"
    },
    {
      "sourceKey": "check-payment",
      "targetKey": "create-shipping-label",
      "conditionExpression": "true"
    },
    {
      "sourceKey": "check-payment",
      "targetKey": "payment-failed-output",
      "conditionExpression": "false"
    },
    {
      "sourceKey": "create-shipping-label",
      "targetKey": "send-confirmation"
    },
    {
      "sourceKey": "send-confirmation",
      "targetKey": "order-complete-output"
    },
    {
      "sourceKey": "inventory-unavailable-output",
      "targetKey": "order-complete-output"
    },
    {
      "sourceKey": "payment-failed-output",
      "targetKey": "order-complete-output"
    }
  ]
}'

