# Produktový kontext

DevBank podporuje zpracování žádostí o korporátní úvěry od založení po rozhodnutí. Ukázkové prostředí pracuje výhradně s fiktivními daty, není spojeno se skutečnou bankou a neprovádí úvěrový scoring.

## Uživatel a pracovní tok

Operátorem je úvěrový poradce. Uživatelské rozhraní vede žádost čtyřmi kroky:

1. založení žádosti;
2. předběžná automatická kontrola;
3. posouzení specialistou;
4. schválení nebo zamítnutí.

Stavový model je `SUBMITTED → UNDER_REVIEW → APPROVED | REJECTED`. Automatické zpracování vlastní přechod do `UNDER_REVIEW`; poradce může rozhodnout pouze o žádosti v tomto stavu. Schválení i zamítnutí vyžadují potvrzení a zamítnutí také odůvodnění.

## Produktové zásady

- UI, validační zprávy a API chyby jsou v češtině; technické identifikátory zůstávají v běžné anglické podobě.
- Každá akce poskytuje jednoznačnou zpětnou vazbu a opakované odeslání nevytváří duplicitní žádost.
- Auditní historie zobrazuje změny stavů srozumitelně; korelační identifikátory jsou dostupné v rozbalitelném detailu.
- Částky se ukládají jako číselné hodnoty a zobrazují jednotně podle českého formátu, například `2 500 000 Kč`.
- Ukázková data tvoří realistické fiktivní společnosti, částky a historie bez vazby na skutečné osoby.

## Hranice řešení

Předběžná kontrola ověřuje validační a procesní připravenost žádosti. Nenahrazuje posouzení bonity ani rozhodovací model banky. Produkční práce s klientskými daty vyžaduje doplnění identity, autorizace, compliance kontrol a provozních opatření popsaných v [bezpečnostním návrhu AWS](aws-security.md).
