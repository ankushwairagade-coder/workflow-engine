# Workflow Analysis & Correction Feature

## Overview

The AI-powered Workflow Analysis feature helps users identify and fix issues in their workflows while editing. It provides intelligent suggestions to improve workflow structure, detect errors, and automatically correct common problems.

## Features

### 1. **Workflow Analysis**
- **Structural Validation**: Detects missing INPUT/OUTPUT nodes
- **Connection Analysis**: Identifies orphaned nodes and disconnected flows
- **Configuration Validation**: Checks for incomplete node configurations
- **Reachability Analysis**: Ensures all nodes are reachable from INPUT

### 2. **Issue Detection**
The system detects various types of issues:

- **MISSING_INPUT** (ERROR): Workflow lacks an INPUT node
- **MISSING_OUTPUT** (WARNING): Workflow should end with OUTPUT node
- **ORPHANED_NODE** (WARNING): Node is not connected to any other node
- **DISCONNECTED_FLOW** (ERROR): Node cannot be reached from INPUT
- **INCOMPLETE_CONFIG** (ERROR/WARNING): Node configuration is missing required fields

### 3. **Automatic Corrections**
The AI suggests and can automatically apply:

- **ADD_NODE**: Add missing nodes (INPUT, OUTPUT, or processing nodes)
- **ADD_EDGE**: Connect orphaned or disconnected nodes
- **FIX_CONFIG**: Fill in missing configuration fields
- **REMOVE_NODE**: Remove unnecessary nodes (future)

### 4. **AI-Powered Analysis**
- Uses Ollama to provide intelligent workflow assessment
- Suggests improvements based on workflow patterns
- Provides best practices recommendations

## API Endpoints

### POST `/api/visual-editor/assistant/analyze`

**Request:**
```json
{
  "nodes": [
    {
      "key": "node-1",
      "type": "HTTP",
      "displayName": "Fetch Data",
      "config": {
        "url": "https://api.example.com/data"
      }
    }
  ],
  "edges": [
    {
      "sourceKey": "node-1",
      "targetKey": "node-2",
      "conditionExpression": null
    }
  ],
  "workflowName": "My Workflow",
  "workflowDescription": "Description"
}
```

**Response:**
```json
{
  "issues": [
    {
      "type": "MISSING_INPUT",
      "severity": "ERROR",
      "message": "Workflow must start with an INPUT node",
      "nodeKey": null,
      "suggestion": "Add an INPUT node as the entry point"
    }
  ],
  "corrections": [
    {
      "type": "ADD_NODE",
      "description": "Add INPUT node to start workflow",
      "nodeToAdd": {
        "type": "INPUT",
        "key": "input-node",
        "displayName": "Start",
        "reason": "Every workflow needs an entry point",
        "suggestedConfig": {},
        "insertAfterNodeKey": null
      }
    }
  ],
  "missingNodes": [...],
  "analysisSummary": "AI-generated analysis...",
  "isValid": false
}
```

## UI Integration

### Visual Editor Assistant Panel

The assistant panel now has **two tabs**:

1. **Suggestions Tab** (existing):
   - Suggested next nodes
   - Code predictions
   - Next steps guidance

2. **Analysis Tab** (new):
   - Workflow validation status
   - List of issues with severity indicators
   - Suggested corrections with one-click apply
   - AI analysis summary

### Usage Flow

1. **Open Visual Editor**: User is editing a workflow
2. **Click AI Assistant**: Floating button appears (bottom-right)
3. **Switch to Analysis Tab**: Click "Analysis" tab
4. **Click "Analyze"**: System analyzes workflow structure
5. **Review Issues**: See all detected issues with severity
6. **Apply Corrections**: Click "Apply Fix" on any correction
7. **Review AI Summary**: Read AI-generated analysis and recommendations

## Example Scenarios

### Scenario 1: Missing INPUT Node
**Issue Detected**: `MISSING_INPUT` (ERROR)
**Correction**: Add INPUT node
**Action**: Click "Apply Fix" → INPUT node added automatically

### Scenario 2: Orphaned Node
**Issue Detected**: `ORPHANED_NODE` (WARNING) - Node "process-data" has no connections
**Correction**: Add edge from INPUT to orphaned node
**Action**: Click "Apply Fix" → Edge created automatically

### Scenario 3: Incomplete HTTP Configuration
**Issue Detected**: `INCOMPLETE_CONFIG` (ERROR) - HTTP node missing URL
**Correction**: Fill in default URL configuration
**Action**: Click "Apply Fix" → Config updated with default URL

### Scenario 4: Disconnected Flow
**Issue Detected**: `DISCONNECTED_FLOW` (ERROR) - Node "send-email" cannot be reached
**Correction**: Add connection path from INPUT
**Action**: Click "Apply Fix" → Connection path created

## Technical Implementation

### Backend Components

1. **VisualEditorAssistantService.analyzeWorkflow()**
   - Performs structural analysis
   - Generates corrections
   - Calls AI for intelligent analysis

2. **Analysis Methods**:
   - `analyzeWorkflowStructure()`: Detects structural issues
   - `findReachableNodes()`: BFS traversal for reachability
   - `generateCorrections()`: Creates correction suggestions
   - `fixNodeConfig()`: Generates default configs for missing fields
   - `getAIAnalysis()`: Uses Ollama for intelligent assessment

### Frontend Components

1. **VisualEditorAssistant Component**
   - Two-tab interface (Suggestions / Analysis)
   - Issue display with severity indicators
   - Correction cards with apply buttons
   - AI analysis summary

2. **Integration with DragDropWorkflowEditor**:
   - `onAddNode`: Adds nodes with optional positioning
   - `onAddEdge`: Creates connections
   - `onFixConfig`: Updates node configurations
   - `onRemoveNode`: Removes nodes (future)

## Benefits

1. **Error Prevention**: Catch issues before saving
2. **Time Savings**: Automatic corrections reduce manual work
3. **Learning Tool**: Users learn best practices from suggestions
4. **Quality Assurance**: Ensures workflows are valid and complete
5. **AI Guidance**: Intelligent recommendations improve workflow design

## Future Enhancements

- [ ] Workflow optimization suggestions
- [ ] Performance analysis
- [ ] Cost estimation for AI nodes
- [ ] Workflow templates based on analysis
- [ ] Batch corrections (apply all fixes at once)
- [ ] Workflow comparison and diff
- [ ] Best practices checker

---

**This feature makes workflow building easier and more reliable by providing intelligent assistance during editing!**

