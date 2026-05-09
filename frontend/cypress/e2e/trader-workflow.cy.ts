describe('Trader workflow', () => {
  const TRADER = 'cypress-trader-wf';
  const apiUrl = () => (Cypress.env('apiBaseUrl') as string) ?? 'http://localhost:8080';

  function isoDatePlusDays(days: number): string {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
  }

  it('receive via API, assign from Term queue, execute from order details', () => {
    const externalRef = `CY-WF-${Date.now()}`;
    const valueDate = isoDatePlusDays(10);

    cy.request({
      method: 'POST',
      url: `${apiUrl()}/api/v1/orders`,
      headers: { 'Content-Type': 'application/json' },
      body: {
        externalOrderReference: externalRef,
        orderType: 'TERM',
        orderOperation: 'SUBSCRIPTION',
        portfolioNumber: 'PF-CY',
        currency: 'EUR',
        amount: 5_000_000,
        valueDate,
        minimumRate: 3.25,
        tenor: '3M',
      },
      failOnStatusCode: true,
    }).then((res) => {
      expect(res.status).to.eq(201);
      expect(res.body.orderId).to.be.a('string').and.not.be.empty;

      const orderId = res.body.orderId as string;

      cy.visit('/term/received', {
        onBeforeLoad(win) {
          win.sessionStorage.setItem('mmx-trader-id', TRADER);
        },
      });

      cy.contains('td.mono', externalRef, { timeout: 20000 }).should('exist');
      cy.contains('tr', externalRef).within(() => {
        cy.contains('button', 'Assign').click();
      });

      cy.visit(`/orders/${orderId}`, {
        onBeforeLoad(win) {
          win.sessionStorage.setItem('mmx-trader-id', TRADER);
        },
      });

      cy.get('input[name="executedRate"]', { timeout: 15000 }).clear();
      cy.get('input[name="executedRate"]').type('3.5');
      cy.get('input[name="counterparty"]').clear();
      cy.get('input[name="counterparty"]').type('BankCo International');

      cy.contains('button', 'Execute order').click();

      cy.get("[data-status='EXECUTED']", { timeout: 20000 }).should('exist');
      cy.contains('dt', 'Dealing reference').should('exist');
      cy.contains('dt', 'Contract number').should('exist');
    });
  });
});
