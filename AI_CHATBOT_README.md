# AI Workflow Chatbot

## Overview

The AI Workflow Chatbot is an intelligent assistant that converts natural language descriptions into complete workflow definitions. It uses Ollama to understand user requirements and generates ready-to-use workflows.

## Features

- **Natural Language Processing**: Describe workflows in plain English
- **Automatic Workflow Generation**: Converts descriptions to JSON workflow definitions
- **Node Type Recognition**: Understands all available node types (HTTP, AI, Email, Script, etc.)
- **Flow Understanding**: Generates proper node connections and execution flow
- **One-Click Creation**: Create workflows directly from chatbot suggestions

## How It Works

1. **User Input**: User describes workflow requirements in natural language
2. **AI Processing**: Ollama processes the description using a specialized prompt
3. **JSON Generation**: AI generates workflow JSON matching FlowStack schema
4. **Validation**: System validates and normalizes the generated workflow
5. **Creation**: User can create the workflow with one click

## Usage

### Via UI

1. Navigate to **Workflows** page
2. Click **"AI Assistant"** button
3. Describe your workflow in the chat interface
4. Review the generated workflow
5. Click **"Create Workflow"** to save it

### Example Prompts

**Compliance Monitoring:**
```
Create a compliance monitoring workflow that:
- Fetches transactions from an API endpoint
- Uses AI to detect suspicious activities
- Sends email alerts if anomalies are found
- Outputs the results
```

**Tax Calculator:**
```
Build a tax calculation workflow with:
- Input node for taxpayer data
- Conditional check for eligibility (income > 0)
- Route to old or new tax regime based on input
- Calculate tax using JavaScript
- Generate AI summary
- Send email with tax breakdown
```

**Customer Onboarding:**
```
Create a customer onboarding workflow:
- Start with input node
- Verify identity via HTTP API call
- If verified, create account via API
- Send welcome email
- If not verified, send rejection email
- Output final status
```

## API Endpoint

### POST `/api/chatbot/chat`

**Request:**
```json
{
  "message": "Create a workflow that fetches data and sends email"
}
```

**Response:**
```json
{
  "response": "I've generated a workflow for you!\n\n**Workflow:** Data Fetcher\n**Nodes:** 4\n**Edges:** 3\n...",
  "workflow": {
    "name": "Data Fetcher",
    "description": "Fetches data and sends email",
    "nodes": [...],
    "edges": [...]
  }
}
```

## Technical Details

### System Prompt

The chatbot uses a carefully crafted system prompt that:
- Explains all available node types
- Provides JSON schema examples
- Includes rules for workflow generation
- Ensures proper node key formatting
- Enforces workflow structure (INPUT → ... → OUTPUT)

### JSON Extraction

The service extracts JSON from AI responses using:
1. Markdown code block detection (```json ... ```)
2. Direct JSON object pattern matching
3. JSON parsing and validation
4. Workflow normalization

### Error Handling

- Invalid JSON: Returns friendly message asking for clarification
- Missing workflow: Returns AI response as conversation message
- Ollama errors: Shows error message with troubleshooting tips

## Configuration

### Ollama Setup

Ensure Ollama is running:
```bash
# Start Ollama service
ollama serve

# Pull a model (recommended: llama2, mistral, or gemma3:1b)
ollama pull gemma3:1b
```

### Application Properties

```properties
# Ollama configuration
flowstack.ollama.base-url=http://localhost:11434
flowstack.ollama.default-model=gemma3:1b
```

## Limitations

1. **Model Quality**: Quality depends on the Ollama model used
   - Smaller models (gemma3:1b) may need clearer prompts
   - Larger models (llama2, mistral) produce better results

2. **Complex Workflows**: Very complex workflows may need refinement
   - Review generated workflows before creating
   - Edit nodes/edges as needed after creation

3. **Ambiguous Descriptions**: Vague descriptions may produce incomplete workflows
   - Be specific about node types
   - Mention execution flow explicitly
   - Include expected inputs/outputs

## Best Practices

1. **Be Specific**: Include node types, flow, and expected behavior
2. **Mention Node Types**: Explicitly state which node types to use
3. **Describe Flow**: Explain the execution sequence
4. **Review Before Creating**: Check generated workflow structure
5. **Iterate**: Refine description if workflow doesn't match expectations

## Troubleshooting

### Chatbot Not Responding
- Check Ollama is running: `curl http://localhost:11434/api/tags`
- Verify model is installed: `ollama list`
- Check application logs for errors

### Poor Quality Workflows
- Use a larger model (llama2, mistral)
- Provide more detailed descriptions
- Break complex workflows into steps

### JSON Parsing Errors
- Model may have returned non-JSON response
- Try rephrasing the request
- Check backend logs for details

## Future Enhancements

- [ ] Support for workflow editing via chat
- [ ] Multi-turn conversations for workflow refinement
- [ ] Workflow templates and examples
- [ ] Integration with ChatGPT for better quality
- [ ] Workflow validation before creation
- [ ] Preview workflow visualization before creation

---

**Note**: The chatbot feature requires Ollama to be running. For production use, consider using ChatGPT API for better quality responses.

