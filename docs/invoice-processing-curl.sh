#!/bin/bash

# Invoice Processing Pipeline Workflow
# This workflow extracts invoice details using AI, structures data, checks approval requirements, routes to approval system, submits to accounting, and notifies parties

curl --location 'http://localhost:8080/api/workflows' \
--header 'Content-Type: application/json' \
--data-raw '{
  "name": "invoice-processing-pipeline",
  "description": "Invoice processing pipeline: extract details using AI, structure data, check approval requirements, route to approval system, submit to accounting, and notify parties",
  "nodes": [
    {
      "key": "receive-invoice",
      "type": "INPUT",
      "displayName": "Receive Invoice Document",
      "config": {
        "defaults": {
          "invoiceDocument": "",
          "invoiceText": "",
          "approvalThreshold": 10000
        }
      }
    },
    {
      "key": "extract-invoice-details",
      "type": "OLLAMA",
      "displayName": "Extract Invoice Details",
      "config": {
        "model": "gemma3:1b",
        "prompt": "Extract the following details from this invoice document and return them in JSON format with keys: vendor, amount, items (array of item objects with name, quantity, price). If any field is missing, use null. Invoice text: {{invoiceText}}{{invoiceDocument}}"
      }
    },
    {
      "key": "structure-data",
      "type": "SCRIPT_JS",
      "displayName": "Structure Extracted Data",
      "config": {
        "script": "const aiResponse = context['extract-invoice-details']?.response || '{}';\\nlet extractedData;\\ntry {\\n  const jsonMatch = aiResponse.match(/\\\\{[\\\\s\\\\S]*\\\\}/);\\n  if (jsonMatch) {\\n    extractedData = JSON.parse(jsonMatch[0]);\\n  } else {\\n    extractedData = JSON.parse(aiResponse);\\n  }\\n} catch (e) {\\n  extractedData = {\\n    vendor: null,\\n    amount: 0,\\n    items: []\\n  };\\n}\\n\\nif (!Array.isArray(extractedData.items)) {\\n  extractedData.items = [];\\n}\\n\\nlet calculatedTotal = extractedData.amount || 0;\\nif (extractedData.items.length > 0) {\\n  calculatedTotal = extractedData.items.reduce((sum, item) => {\\n    const price = parseFloat(item.price || 0);\\n    const qty = parseFloat(item.quantity || 1);\\n    return sum + (price * qty);\\n  }, 0);\\n}\\n\\nconst structuredData = {\\n  vendor: extractedData.vendor || 'Unknown Vendor',\\n  amount: extractedData.amount || calculatedTotal,\\n  items: extractedData.items || [],\\n  itemCount: extractedData.items.length || 0,\\n  extractedAt: new Date().toISOString()\\n};\\n\\nreturn structuredData;"
      }
    },
    {
      "key": "check-approval-needed",
      "type": "IF_ELSE",
      "displayName": "Check if Amount Requires Approval",
      "config": {
        "condition": "{{structure-data::amount}} >= {{approvalThreshold}}"
      }
    },
    {
      "key": "route-to-approval",
      "type": "HTTP",
      "displayName": "Route to Approval System",
      "config": {
        "url": "https://api.example.com/approval/request",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"vendor\": \"{{structure-data::vendor}}\", \"amount\": {{structure-data::amount}}, \"items\": {{structure-data::items}}, \"invoiceId\": \"{{invoiceDocument}}\"}"
      }
    },
    {
      "key": "submit-to-accounting",
      "type": "HTTP",
      "displayName": "Submit to Accounting System",
      "config": {
        "url": "https://api.example.com/accounting/submit",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"vendor\": \"{{structure-data::vendor}}\", \"amount\": {{structure-data::amount}}, \"items\": {{structure-data::items}}, \"approvalStatus\": \"{{route-to-approval::status}}\", \"invoiceId\": \"{{invoiceDocument}}\"}"
      }
    },
    {
      "key": "notify-parties",
      "type": "EMAIL",
      "displayName": "Notify Relevant Parties",
      "config": {
        "to": "ankush.wairagade@paytm.com",
        "subject": "Invoice Processed - {{structure-data::vendor}} - ${{structure-data::amount}}",
        "body": "Invoice Processing Complete\\n\\nVendor: {{structure-data::vendor}}\\nAmount: ${{structure-data::amount}}\\nItems: {{structure-data::itemCount}}\\n\\nApproval Required: {{check-approval-needed::result}}\\nApproval Status: {{route-to-approval::status}}\\nAccounting Status: {{submit-to-accounting::status}}\\n\\nInvoice Details:\\n{{structure-data::items}}\\n\\nProcessed at: {{structure-data::extractedAt}}\\n\\n---\\nInvoice Processing System",
        "from": "invoices@yourcompany.com"
      }
    },
    {
      "key": "invoice-processed-output",
      "type": "OUTPUT",
      "displayName": "Invoice Processed",
      "config": {
        "fields": ["structure-data", "route-to-approval", "submit-to-accounting", "notify-parties"]
      }
    }
  ],
  "edges": [
    {
      "sourceKey": "receive-invoice",
      "targetKey": "extract-invoice-details"
    },
    {
      "sourceKey": "extract-invoice-details",
      "targetKey": "structure-data"
    },
    {
      "sourceKey": "structure-data",
      "targetKey": "check-approval-needed"
    },
    {
      "sourceKey": "check-approval-needed",
      "targetKey": "route-to-approval",
      "conditionExpression": "true"
    },
    {
      "sourceKey": "check-approval-needed",
      "targetKey": "submit-to-accounting",
      "conditionExpression": "false"
    },
    {
      "sourceKey": "route-to-approval",
      "targetKey": "submit-to-accounting"
    },
    {
      "sourceKey": "submit-to-accounting",
      "targetKey": "notify-parties"
    },
    {
      "sourceKey": "notify-parties",
      "targetKey": "invoice-processed-output"
    }
  ]
}'

