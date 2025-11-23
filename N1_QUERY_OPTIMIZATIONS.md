# N+1 Query Optimizations

This document outlines all N+1 query problems that were identified and fixed in the FlowStack workflow engine.

## N+1 Problem Overview

N+1 queries occur when:
1. Fetching a list of entities (1 query)
2. Then for each entity, fetching related entities in separate queries (N queries)
3. Result: 1 + N queries instead of 1 optimized query

## Fixed Issues

### 1. WorkflowRunService.listRuns() - N+1 Problem ✅

**Problem:**
- `findAll()` fetches all runs (1 query)
- Each `mapper.toResponse(run)` accesses `run.getWorkflowDefinition()` causing lazy loading (N queries)
- Total: 1 + N queries

**Solution:**
- Added `findAllWithWorkflowDefinition()` with `LEFT JOIN FETCH`
- Fetches all runs with their workflow definitions in a single query
- Total: 1 query

**Query:**
```java
@Query("select distinct r from WorkflowRun r left join fetch r.workflowDefinition order by r.createdAt desc")
List<WorkflowRun> findAllWithWorkflowDefinition();
```

### 2. WorkflowRunService.getRun() - N+1 Problem ✅

**Problem:**
- `findById()` fetches run (1 query)
- Accessing `run.getWorkflowDefinition()` causes lazy loading (1 query)
- Total: 2 queries

**Solution:**
- Added `findByIdWithWorkflowDefinition()` with `LEFT JOIN FETCH`
- Fetches run with workflow definition in a single query
- Total: 1 query

**Query:**
```java
@Query("select distinct r from WorkflowRun r left join fetch r.workflowDefinition where r.id = :id")
Optional<WorkflowRun> findByIdWithWorkflowDefinition(@Param("id") Long id);
```

### 3. WorkflowRunService.updateContext() - N+1 Problem ✅

**Problem:**
- Same as `getRun()` - accessing workflow definition causes lazy loading

**Solution:**
- Uses `findByIdWithWorkflowDefinition()` instead of `findById()`

### 4. WorkflowDefinitionService.deleteWorkflow() - N+1 Problem ✅

**Problem:**
- Fetches all runs for a workflow (1 query)
- For each run, fetches node runs in a loop (N queries)
- Total: 1 + N queries

**Solution:**
- Added `findByWorkflowRunIds()` to batch fetch node runs
- Collects all run IDs and fetches all node runs in a single query
- Total: 2 queries (1 for runs, 1 for all node runs)

**Query:**
```java
@Query("select n from WorkflowNodeRun n where n.workflowRun.id in :runIds")
List<WorkflowNodeRun> findByWorkflowRunIds(@Param("runIds") List<Long> runIds);
```

### 5. WorkflowExecutor.execute() - N+1 Problem ✅

**Problem:**
- `findById()` fetches run (1 query)
- Accessing `run.getWorkflowDefinition()` causes lazy loading (1 query)
- Total: 2 queries

**Solution:**
- Uses `findByIdWithWorkflowDefinition()` instead of `findById()`
- Total: 1 query

### 6. WorkflowDefinitionRepository.findAllWithNodesAndEdges() - Optimization ✅

**Enhancement:**
- Added `ORDER BY` clause for consistent ordering
- Ensures results are always sorted by `updatedAt desc`

**Query:**
```java
@Query("select distinct w from WorkflowDefinition w left join fetch w.nodes left join fetch w.edges order by w.updatedAt desc")
List<WorkflowDefinition> findAllWithNodesAndEdges();
```

## Performance Impact

### Before Optimizations:
- **listRuns()**: 1 + N queries (where N = number of runs)
- **getRun()**: 2 queries per run
- **deleteWorkflow()**: 1 + N queries (where N = number of runs)
- **execute()**: 2 queries per execution

### After Optimizations:
- **listRuns()**: 1 query (regardless of number of runs)
- **getRun()**: 1 query
- **deleteWorkflow()**: 2 queries (regardless of number of runs)
- **execute()**: 1 query

### Example Performance Improvement:
For a workflow with 100 runs:
- **Before**: 1 + 100 = 101 queries for `listRuns()`
- **After**: 1 query for `listRuns()`
- **Improvement**: 99% reduction in queries

## Best Practices Applied

1. **JOIN FETCH**: Used `LEFT JOIN FETCH` to eagerly load related entities
2. **Batch Fetching**: Used `IN` clause to fetch multiple related entities in one query
3. **Distinct**: Used `distinct` to avoid duplicate results from JOINs
4. **Read-Only Transactions**: Marked read-only methods with `@Transactional(readOnly = true)`

## Files Modified

1. **WorkflowRunRepository.java**:
   - Added `findAllWithWorkflowDefinition()`
   - Added `findByIdWithWorkflowDefinition()`
   - Added `findByWorkflowDefinitionIdWithDefinition()`

2. **WorkflowNodeRunRepository.java**:
   - Added `findByWorkflowRunIds()` for batch fetching

3. **WorkflowRunService.java**:
   - Updated `listRuns()` to use optimized query
   - Updated `getRun()` to use optimized query
   - Updated `updateContext()` to use optimized query

4. **WorkflowDefinitionService.java**:
   - Updated `deleteWorkflow()` to use batch fetching

5. **WorkflowExecutor.java**:
   - Updated `execute()` to use optimized query

6. **WorkflowDefinitionRepository.java**:
   - Added ordering to `findAllWithNodesAndEdges()`

## Monitoring

To verify N+1 queries are fixed:
1. Enable SQL logging: `spring.jpa.show-sql=true`
2. Check query count in logs
3. Use database query profiler
4. Monitor application performance metrics

## Future Optimizations

Consider these additional optimizations:
1. **Pagination**: Add pagination to `listRuns()` and `listDefinitions()` for large datasets
2. **Entity Graphs**: Use `@EntityGraph` annotations for more complex fetch strategies
3. **Second-Level Cache**: Consider caching frequently accessed entities
4. **Query Result Caching**: Cache query results for read-heavy operations
5. **Batch Processing**: Use batch processing for bulk operations

