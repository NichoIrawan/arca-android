package org.nicho.arca.domain.parsing

// CurrencyParser: "50rb", "1,5jt", "50k", "50.000" → Long. The only parser for external input; tooLarge via checked math.
// SDD-ARC-002 §7.5, DD-14 · NFR-U05
