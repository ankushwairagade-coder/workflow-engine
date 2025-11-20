# India Tax Calculator Workflow

This workflow demonstrates conditional branching using IF/ELSE nodes to handle:
1. **Tax Eligibility Check** - Determines if the taxpayer is eligible (income > 0)
2. **Tax Regime Selection** - Routes to either Old or New tax regime calculation

## Workflow Structure

```
[Tax Input] 
    ↓
[Check Eligibility: income > 0?]
    ├─ TRUE → [Check Regime: regime == "old"?]
    │           ├─ TRUE → [Calculate Old Tax] → [Tax Summary] → [Email]
    │           └─ FALSE → [Calculate New Tax] → [Tax Summary] → [Email]
    └─ FALSE → [Not Eligible Output]
```

## Nodes

1. **tax-input** (INPUT) - Receives taxpayer details
2. **check-eligibility** (IF_ELSE) - Checks if `grossIncome > 0`
3. **check-regime** (IF_ELSE) - Checks if `regime == "old"`
4. **calc-old-tax** (SCRIPT_JS) - Calculates tax using old regime slabs
5. **calc-new-tax** (SCRIPT_JS) - Calculates tax using new regime slabs
6. **not-eligible-output** (OUTPUT) - Outputs message for non-eligible taxpayers
7. **tax-summary** (OLLAMA) - Generates AI summary
8. **email-tax-summary** (EMAIL) - Sends email with tax summary

## Usage

### 1. Create the Workflow

```bash
# Using curl script
chmod +x docs/india-tax-workflow-curl.sh
./docs/india-tax-workflow-curl.sh

# Or using curl directly
curl -X POST http://localhost:8080/api/workflows \
  -H "Content-Type: application/json" \
  -d @docs/india-tax-workflow-example.json
```

### 2. Run the Workflow

#### Example 1: Old Regime (Eligible)
```bash
curl -X POST http://localhost:8080/api/runs/{workflowId} \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "taxpayer": "John Doe",
      "financialYear": "FY24-25",
      "grossIncome": 1850000,
      "regime": "old",
      "deductions": {
        "section80C": 150000,
        "section80D": 35000,
        "hra": 240000
      }
    }
  }'
```

**Flow**: Input → Eligibility Check (TRUE) → Regime Check (TRUE) → Old Tax Calc → Summary → Email

#### Example 2: New Regime (Eligible)
```bash
curl -X POST http://localhost:8080/api/runs/{workflowId} \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "taxpayer": "Jane Smith",
      "financialYear": "FY24-25",
      "grossIncome": 1200000,
      "regime": "new"
    }
  }'
```

**Flow**: Input → Eligibility Check (TRUE) → Regime Check (FALSE) → New Tax Calc → Summary → Email

#### Example 3: Not Eligible
```bash
curl -X POST http://localhost:8080/api/runs/{workflowId} \
  -H "Content-Type: application/json" \
  -d '{
    "input": {
      "taxpayer": "Test User",
      "grossIncome": 0
    }
  }'
```

**Flow**: Input → Eligibility Check (FALSE) → Not Eligible Output

## Tax Slabs

### Old Regime (with deductions)
- ₹0 - ₹2,50,000: 0%
- ₹2,50,001 - ₹5,00,000: 5%
- ₹5,00,001 - ₹10,00,000: 20%
- Above ₹10,00,000: 30%
- Health & Education Cess: 4% on tax

### New Regime (no deductions)
- ₹0 - ₹3,00,000: 0%
- ₹3,00,001 - ₹7,00,000: 5%
- ₹7,00,001 - ₹10,00,000: 10%
- ₹10,00,001 - ₹12,00,000: 15%
- ₹12,00,001 - ₹15,00,000: 20%
- Above ₹15,00,000: 30%
- Health & Education Cess: 4% on tax

## IF/ELSE Node Conditions

### Eligibility Check
- **Condition**: `{{grossIncome}} > 0`
- **True Edge**: Routes to regime check
- **False Edge**: Routes to not-eligible output

### Regime Check
- **Condition**: `{{regime}} == "old"`
- **True Edge**: Routes to old tax calculation
- **False Edge**: Routes to new tax calculation

## Edge Conditions

Edges from IF/ELSE nodes use `conditionExpression`:
- `"true"` - Follow when IF/ELSE result is true
- `"false"` - Follow when IF/ELSE result is false

## Output Variables

### Old Tax Calculation Outputs:
- `calc-old-tax::regime` = "old"
- `calc-old-tax::taxableIncome` = Taxable income after deductions
- `calc-old-tax::deductions` = Total deductions
- `calc-old-tax::baseTax` = Base tax amount
- `calc-old-tax::cess` = Cess amount
- `calc-old-tax::totalTax` = Total tax payable

### New Tax Calculation Outputs:
- `calc-new-tax::regime` = "new"
- `calc-new-tax::taxableIncome` = Gross income (no deductions)
- `calc-new-tax::deductions` = 0
- `calc-new-tax::baseTax` = Base tax amount
- `calc-new-tax::cess` = Cess amount
- `calc-new-tax::totalTax` = Total tax payable

## Notes

- The workflow automatically routes based on IF/ELSE node results
- Both tax calculation paths converge at the `tax-summary` node
- The summary uses template variables that will be populated by whichever calculation ran
- Email is sent only for eligible taxpayers who complete tax calculation

