/**
 * MSE Transport shared product copy: the glossary (info tooltips) and the "How it works" flow.
 * Source of truth: spring-cod-settlement/brand/content/suite-content.ts, copied unchanged into
 * each app (Settle web, Dispatch console, Copilot web). Framework-free on purpose.
 */
export type SuiteLang = 'en' | 'fr';
export type ProductKey = 'dispatch' | 'settle' | 'copilot';

export type TermKey =
  | 'cod' | 'remittance' | 'statement' | 'reconciliation' | 'dispute' | 'netRemitted'
  | 'discrepancy' | 'attempts' | 'returned' | 'expected' | 'reported' | 'fees' | 'balanceOwed';

export const GLOSSARY: Record<TermKey, Record<SuiteLang, { term: string; definition: string }>> = {
  cod: {
    en: { term: 'COD (cash on delivery)', definition: 'The customer pays in cash when the parcel arrives. The courier collects the money and pays it back to the merchant later.' },
    fr: { term: 'COD (paiement à la livraison)', definition: 'Le client paie en espèces à la réception du colis. Le livreur encaisse l’argent et le reverse plus tard au marchand.' },
  },
  remittance: {
    en: { term: 'Remittance', definition: 'The money a courier company pays back to the merchant: the cash it collected, minus its fees.' },
    fr: { term: 'Reversement', definition: 'L’argent que la société de livraison reverse au marchand : les espèces encaissées, moins ses frais.' },
  },
  statement: {
    en: { term: 'Statement', definition: 'The file (CSV) a courier sends with each remittance: one line per delivered parcel, with the cash collected and the fee kept.' },
    fr: { term: 'Relevé', definition: 'Le fichier (CSV) envoyé par le livreur avec chaque reversement : une ligne par colis livré, avec l’encaissement et les frais retenus.' },
  },
  reconciliation: {
    en: { term: 'Reconciliation', definition: 'Checking every line of a statement against your own orders, to find what matches and what doesn’t.' },
    fr: { term: 'Rapprochement', definition: 'Vérifier chaque ligne d’un relevé avec vos propres commandes, pour trouver ce qui correspond et ce qui ne correspond pas.' },
  },
  dispute: {
    en: { term: 'Dispute', definition: 'A formal claim against the courier for a line that doesn’t match. Finance reviews it and decides whether the courier pays.' },
    fr: { term: 'Litige', definition: 'Une réclamation formelle auprès du livreur pour une ligne qui ne correspond pas. La finance l’étudie et décide si le livreur paie.' },
  },
  netRemitted: {
    en: { term: 'Net remitted', definition: 'What actually reached your bank: the cash the courier reported, minus its fees.' },
    fr: { term: 'Net reversé', definition: 'Ce qui arrive réellement sur votre compte : les espèces déclarées par le livreur, moins ses frais.' },
  },
  discrepancy: {
    en: { term: 'Discrepancy', definition: 'A line that doesn’t match: paid short, an unknown tracking number, or a delivered parcel missing from the statement.' },
    fr: { term: 'Écart', definition: 'Une ligne qui ne correspond pas : montant insuffisant, numéro de suivi inconnu, ou colis livré absent du relevé.' },
  },
  attempts: {
    en: { term: 'Delivery attempts (1/3)', definition: 'How many times the courier has tried to deliver, out of the maximum. After the last failed attempt the parcel goes back to the merchant.' },
    fr: { term: 'Tentatives (1/3)', definition: 'Le nombre de tentatives de livraison, sur le maximum autorisé. Après le dernier échec, le colis retourne au marchand.' },
  },
  returned: {
    en: { term: 'Returned', definition: 'The parcel could not be delivered after every attempt and was sent back to the merchant. No cash is collected.' },
    fr: { term: 'Retourné', definition: 'Le colis n’a pas pu être livré après toutes les tentatives et a été renvoyé au marchand. Aucun encaissement.' },
  },
  expected: {
    en: { term: 'Expected', definition: 'The cash that should have been collected: the amount due on every delivered order.' },
    fr: { term: 'Attendu', definition: 'Les espèces qui auraient dû être encaissées : le montant dû de chaque commande livrée.' },
  },
  reported: {
    en: { term: 'Reported', definition: 'The cash the courier says it collected, from the statements it sent.' },
    fr: { term: 'Déclaré', definition: 'Les espèces que le livreur déclare avoir encaissées, d’après ses relevés.' },
  },
  fees: {
    en: { term: 'Fees', definition: 'What the courier keeps for each parcel it handled, deducted from the cash it pays back.' },
    fr: { term: 'Frais', definition: 'Ce que le livreur retient pour chaque colis traité, déduit des espèces qu’il reverse.' },
  },
  balanceOwed: {
    en: { term: 'Still owed', definition: 'Expected minus reported: cash the courier collected (or should have) but hasn’t accounted for yet.' },
    fr: { term: 'Reste dû', definition: 'Attendu moins déclaré : l’argent que le livreur a encaissé (ou aurait dû) mais pas encore justifié.' },
  },
};

export interface FlowStep { product: ProductKey | 'courier'; title: string; detail: string }

/** The end-to-end flow across the three products, for the "How it works" panel. */
export const HOW_IT_WORKS: Record<SuiteLang, { title: string; intro: string; steps: FlowStep[] }> = {
  en: {
    title: 'How MSE Transport works',
    intro: 'Three products, one flow: from the order to the cash in the merchant’s bank.',
    steps: [
      { product: 'dispatch', title: 'Order created', detail: 'The merchant’s order enters MSE Dispatch and a courier is assigned by zone.' },
      { product: 'courier', title: 'Delivered, cash collected', detail: 'The courier delivers and collects the cash at the door, or retries and finally returns the parcel.' },
      { product: 'courier', title: 'Statement sent', detail: 'The courier company sends a remittance statement (CSV): what it collected and the fees it kept.' },
      { product: 'settle', title: 'Reconciled line by line', detail: 'MSE Settle checks every line against the merchant’s orders and shows who still owes what.' },
      { product: 'settle', title: 'Mismatches become disputes', detail: 'Any short payment or missing parcel becomes a dispute that finance resolves.' },
      { product: 'copilot', title: 'Ask anything', detail: 'MSE Copilot answers questions over all of it, with exact, checked figures.' },
    ],
  },
  fr: {
    title: 'Comment fonctionne MSE Transport',
    intro: 'Trois produits, un seul parcours : de la commande jusqu’à l’argent sur le compte du marchand.',
    steps: [
      { product: 'dispatch', title: 'Commande créée', detail: 'La commande du marchand arrive dans MSE Dispatch et un livreur est affecté selon la zone.' },
      { product: 'courier', title: 'Livrée, espèces encaissées', detail: 'Le livreur livre et encaisse à la porte, ou réessaie puis retourne le colis.' },
      { product: 'courier', title: 'Relevé envoyé', detail: 'La société de livraison envoie un relevé de reversement (CSV) : les encaissements et les frais retenus.' },
      { product: 'settle', title: 'Rapproché ligne par ligne', detail: 'MSE Settle vérifie chaque ligne avec les commandes du marchand et montre qui doit encore quoi.' },
      { product: 'settle', title: 'Les écarts deviennent des litiges', detail: 'Chaque paiement insuffisant ou colis manquant devient un litige tranché par la finance.' },
      { product: 'copilot', title: 'Posez vos questions', detail: 'MSE Copilot répond sur l’ensemble, avec des chiffres exacts et vérifiés.' },
    ],
  },
};
