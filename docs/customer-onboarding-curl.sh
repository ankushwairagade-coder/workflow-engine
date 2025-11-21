#!/bin/bash

# Customer Onboarding Workflow
# This workflow collects customer data, validates identity, extracts document information using AI, formats for CRM, creates account, and sends welcome email

curl --location 'http://localhost:8080/api/workflows' \
--header 'Content-Type: application/json' \
--data-raw '{
  "name": "customer-onboarding",
  "description": "Customer onboarding workflow: collect data, validate identity, extract document information using AI, format for CRM, create account, and send welcome email",
  "nodes": [
    {
      "key": "collect-customer-data",
      "type": "INPUT",
      "displayName": "Collect Customer Data",
      "config": {
        "defaults": {
          "customerName": "",
          "customerType": "individual",
          "email": "ankush.wairagade@paytm.com",
          "phone": "",
          "documents": "",
          "documentText": ""
        }
      }
    },
    {
      "key": "check-customer-type",
      "type": "IF_ELSE",
      "displayName": "Check if Business or Individual Customer",
      "config": {
        "condition": "{{customerType}} == \"business\""
      }
    },
    {
      "key": "validate-identity",
      "type": "HTTP",
      "displayName": "Validate Identity via External Service",
      "config": {
        "url": "https://api.example.com/identity/validate",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"customerName\": \"{{customerName}}\", \"customerType\": \"{{customerType}}\", \"email\": \"{{email}}\", \"phone\": \"{{phone}}\"}"
      }
    },
    {
      "key": "extract-document-info",
      "type": "OLLAMA",
      "displayName": "Extract Information from Uploaded Documents",
      "config": {
        "model": "gemma3:1b",
        "prompt": "Extract key information from the following customer documents. Return a JSON object with fields: fullName, dateOfBirth (if individual) or registrationNumber (if business), address, identificationNumber, documentType. If any field is missing, use null. Document text: {{documentText}}{{documents}}"
      }
    },
    {
      "key": "format-for-crm",
      "type": "SCRIPT_JS",
      "displayName": "Format Data for CRM",
      "config": {
        "script": "const customerData = context['collect-customer-data'] || {};\\nconst identityResult = context['validate-identity']?.body || '{}';\\nconst documentInfo = context['extract-document-info']?.response || '{}';\\n\\nlet parsedIdentity = {};\\nlet parsedDocument = {};\\n\\ntry {\\n  parsedIdentity = typeof identityResult === 'string' ? JSON.parse(identityResult) : identityResult;\\n} catch (e) {\\n  parsedIdentity = {};\\n}\\n\\ntry {\\n  const docMatch = documentInfo.match(/\\\\{[\\\\s\\\\S]*\\\\}/);\\n  if (docMatch) {\\n    parsedDocument = JSON.parse(docMatch[0]);\\n  } else {\\n    parsedDocument = JSON.parse(documentInfo);\\n  }\\n} catch (e) {\\n  parsedDocument = {};\\n}\\n\\nconst crmData = {\\n  name: parsedDocument.fullName || customerData.customerName || 'Unknown',\\n  email: customerData.email || '',\\n  phone: customerData.phone || '',\\n  customerType: customerData.customerType || 'individual',\\n  dateOfBirth: parsedDocument.dateOfBirth || null,\\n  registrationNumber: parsedDocument.registrationNumber || null,\\n  address: parsedDocument.address || null,\\n  identificationNumber: parsedDocument.identificationNumber || null,\\n  documentType: parsedDocument.documentType || null,\\n  identityValidated: parsedIdentity.validated || false,\\n  identityStatus: parsedIdentity.status || 'pending',\\n  formattedAt: new Date().toISOString()\\n};\\n\\nreturn crmData;"
      }
    },
    {
      "key": "create-crm-account",
      "type": "HTTP",
      "displayName": "Create Account in CRM",
      "config": {
        "url": "https://api.example.com/crm/accounts",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer YOUR_API_TOKEN",
          "Content-Type": "application/json"
        },
        "body": "{\"name\": \"{{format-for-crm::name}}\", \"email\": \"{{format-for-crm::email}}\", \"phone\": \"{{format-for-crm::phone}}\", \"customerType\": \"{{format-for-crm::customerType}}\", \"dateOfBirth\": \"{{format-for-crm::dateOfBirth}}\", \"registrationNumber\": \"{{format-for-crm::registrationNumber}}\", \"address\": \"{{format-for-crm::address}}\", \"identificationNumber\": \"{{format-for-crm::identificationNumber}}\", \"documentType\": \"{{format-for-crm::documentType}}\", \"identityValidated\": {{format-for-crm::identityValidated}}}"
      }
    },
    {
      "key": "send-welcome-email",
      "type": "EMAIL",
      "displayName": "Send Welcome Email",
      "config": {
        "to": "{{email}}",
        "subject": "Welcome to Our Platform - {{format-for-crm::name}}",
        "body": "Dear {{format-for-crm::name}},\\n\\nWelcome to our platform!\\n\\nYour account has been successfully created.\\n\\nAccount Details:\\n- Name: {{format-for-crm::name}}\\n- Email: {{email}}\\n- Customer Type: {{format-for-crm::customerType}}\\n- Account Status: {{create-crm-account::status}}\\n\\nYour identity has been validated and your account is ready to use.\\n\\nIf you have any questions, please don't hesitate to contact our support team.\\n\\nBest regards,\\nCustomer Onboarding Team",
        "from": "onboarding@yourcompany.com"
      }
    },
    {
      "key": "onboarding-complete-output",
      "type": "OUTPUT",
      "displayName": "Onboarding Complete",
      "config": {
        "fields": ["collect-customer-data", "validate-identity", "extract-document-info", "format-for-crm", "create-crm-account", "send-welcome-email"]
      }
    }
  ],
  "edges": [
    {
      "sourceKey": "collect-customer-data",
      "targetKey": "check-customer-type"
    },
    {
      "sourceKey": "check-customer-type",
      "targetKey": "validate-identity"
    },
    {
      "sourceKey": "validate-identity",
      "targetKey": "extract-document-info"
    },
    {
      "sourceKey": "extract-document-info",
      "targetKey": "format-for-crm"
    },
    {
      "sourceKey": "format-for-crm",
      "targetKey": "create-crm-account"
    },
    {
      "sourceKey": "create-crm-account",
      "targetKey": "send-welcome-email"
    },
    {
      "sourceKey": "send-welcome-email",
      "targetKey": "onboarding-complete-output"
    }
  ]
}'

