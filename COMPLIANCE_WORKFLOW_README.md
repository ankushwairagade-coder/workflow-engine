# Compliance Transaction Monitoring Workflow

This workflow automatically monitors transactions for suspicious activities and compliance issues using Ollama AI.

## Workflow Steps

1. **START (INPUT)** → Scheduled trigger entry point
2. **API_CALL (HTTP)** → Fetches transactions from external API
3. **AI (OLLAMA)** → Detects anomalies/compliance issues (returns true/false)
4. **CONDITION (IF_ELSE)** → Flags suspicious activities
5. **AI (OLLAMA)** → Gets detailed analysis (only if suspicious)
6. **TRANSFORM (SCRIPT_JS)** → Generates compliance report
7. **EMAIL** → Alerts compliance team
8. **END (OUTPUT)** → Workflow completion

## Configuration Required

Before using this curl command, update the following:

1. **Transaction API URL**: Replace `https://api.example.com/transactions` with your actual transaction API endpoint
2. **API Token**: Replace `YOUR_API_TOKEN` with your actual API bearer token
3. **Email Addresses**: 
   - Replace `compliance@yourcompany.com` with your compliance team email
   - Replace `alerts@yourcompany.com` with your alerts sender email
4. **Ollama Model**: Replace `llama2` with your installed Ollama model (e.g., `mistral`, `codellama`, `gemma3:1b`)

## Usage

1. Make sure Ollama is running on `http://localhost:11434`
2. Ensure you have the specified model installed: `ollama pull llama2`
3. Run the curl command from `compliance-workflow-curl.txt` or `compliance-workflow-curl.sh`

## Testing

After creating the workflow, you can manually trigger it:

```bash
curl -X POST http://localhost:8080/api/workflows/{workflowId}/execute \
-H "Content-Type: application/json" \
-d '{"input": {}}'
```

## Notes

- The workflow uses **Ollama** (not ChatGPT) for AI analysis
- The first OLLAMA node returns a simple "true" or "false" for the condition check
- The second OLLAMA node (only executed if suspicious) provides detailed analysis
- The SCRIPT_JS node generates the compliance report (note: JavaScript execution may be a placeholder in the current implementation)
- Email configuration must be set up in `application.properties`

