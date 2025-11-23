# Streaming AI Assistant Implementation

This document describes the streaming response implementation for the AI Assistant chatbot, providing a ChatGPT-like progressive typing experience.

## Overview

The AI Assistant now streams responses progressively instead of loading the entire answer at once, providing a more natural and engaging user experience similar to ChatGPT.

## Architecture

### Backend (Java/Spring Boot)

1. **OllamaClient.java**:
   - Added `generateTextStream()` method that uses Ollama's streaming API
   - Returns a `Flux<String>` that emits text chunks as they're generated
   - Uses HTTP streaming to read Ollama's response line by line

2. **ChatbotService.java**:
   - Added `processMessageStream()` method
   - Uses `SseEmitter` (Server-Sent Events) to stream chunks to the frontend
   - Processes chunks in real-time and sends them to the client
   - Extracts workflow JSON after streaming completes

3. **ChatbotController.java**:
   - Added `/api/chatbot/chat/stream` endpoint
   - Returns `SseEmitter` with `TEXT_EVENT_STREAM` content type
   - Handles streaming connection

### Frontend (React/TypeScript)

1. **chatbotService.ts**:
   - Added `chatStream()` method
   - Uses Fetch API with streaming support
   - Parses Server-Sent Events (SSE) format
   - Provides callbacks for chunks, completion, and errors

2. **WorkflowChatbot.tsx**:
   - Updated to use streaming by default
   - Displays text progressively as it arrives
   - Shows blinking cursor during streaming
   - Updates message content in real-time

## How It Works

### Backend Flow:
```
1. User sends message → ChatbotController
2. Controller creates SseEmitter → ChatbotService.processMessageStream()
3. Service calls OllamaClient.generateTextStream()
4. Ollama streams response chunks → Flux emits chunks
5. Each chunk sent via SseEmitter.event("chunk", data)
6. After completion → Extract workflow → Send "complete" event
7. SseEmitter closes connection
```

### Frontend Flow:
```
1. User sends message → chatbotService.chatStream()
2. Fetch API opens streaming connection
3. Parse SSE events (event: chunk, data: <text>)
4. For each chunk → Update message content progressively
5. Show blinking cursor while streaming
6. On "complete" event → Finalize message, show workflow if available
```

## Features

### Progressive Display
- Text appears word-by-word as AI generates it
- No waiting for complete response
- Feels natural and responsive

### Visual Feedback
- Blinking cursor during streaming
- Loading indicator when starting
- Smooth scrolling to latest message

### Error Handling
- Graceful error messages if streaming fails
- Fallback to non-streaming if needed
- Connection timeout handling

## API Endpoints

### POST `/api/chatbot/chat/stream`
- **Content-Type**: `application/json`
- **Response**: `text/event-stream` (SSE)
- **Request Body**: `{ "message": "user message" }`

**SSE Event Types:**
- `chunk`: Text chunk from AI (streamed progressively)
- `workflow`: Workflow JSON (sent after completion if workflow found)
- `complete`: Final message (sent when streaming done)
- `error`: Error message (sent on error)

**Example SSE Response:**
```
event: chunk
data: I've

event: chunk
data: generated

event: chunk
data: a workflow

event: workflow
data: {"name":"...","nodes":[...]}

event: complete
data: I've generated a workflow for you!...
```

## Configuration

### Dependencies Added:
- `reactor-core` - For reactive streaming support

### Timeout Settings:
- SseEmitter timeout: 5 minutes (300000ms)
- Ollama connection timeout: 10 seconds
- Ollama read timeout: 30-120 seconds (dynamic)

## Performance

### Benefits:
- **Perceived Performance**: Users see response immediately
- **Better UX**: Feels more interactive and engaging
- **Progressive Loading**: No long wait times

### Considerations:
- Streaming requires persistent HTTP connection
- More network overhead (but better UX)
- Server resources for long-lived connections

## Testing

To test streaming:
1. Start the backend server
2. Ensure Ollama is running
3. Open the chatbot in the frontend
4. Send a message
5. Observe text appearing progressively

## Future Enhancements

1. **Typing Speed Control**: Adjust chunk display speed
2. **Pause/Resume**: Allow pausing streaming
3. **Stream Cancellation**: Cancel ongoing streams
4. **Connection Retry**: Auto-retry on connection loss
5. **Progress Indicator**: Show streaming progress

## Troubleshooting

### Streaming Not Working:
1. Check Ollama is running and accessible
2. Verify CORS settings allow streaming
3. Check browser console for errors
4. Verify network supports long-lived connections

### Text Not Appearing:
1. Check SSE parsing in browser console
2. Verify event format matches expected format
3. Check for JavaScript errors

### Connection Timeout:
1. Increase SseEmitter timeout if needed
2. Check network stability
3. Verify Ollama response time

