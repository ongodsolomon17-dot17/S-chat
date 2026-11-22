// Phone input with a country-code selector. Produces international format (+<code><number>), which is what
// SMS / WhatsApp account recovery needs.
import { html, raw, esc } from "../core/util.js";

const C = [["Nigeria","NG",234],["Ghana","GH",233],["Kenya","KE",254],["South Africa","ZA",27],["Egypt","EG",20],["Ethiopia","ET",251],["Tanzania","TZ",255],["Uganda","UG",256],["Cameroon","CM",237],["Senegal","SN",221],["Côte d'Ivoire","CI",225],["Morocco","MA",212],["Algeria","DZ",213],["Rwanda","RW",250],["Zambia","ZM",260],["Zimbabwe","ZW",263],["Togo","TG",228],["Benin","BJ",229],
  ["United States","US",1],["Canada","CA",1],["Mexico","MX",52],["Brazil","BR",55],["Argentina","AR",54],["Colombia","CO",57],
  ["United Kingdom","GB",44],["Ireland","IE",353],["France","FR",33],["Germany","DE",49],["Spain","ES",34],["Italy","IT",39],["Netherlands","NL",31],["Portugal","PT",351],["Sweden","SE",46],["Norway","NO",47],["Poland","PL",48],["Turkey","TR",90],
  ["United Arab Emirates","AE",971],["Saudi Arabia","SA",966],["Qatar","QA",974],["Israel","IL",972],["India","IN",91],["Pakistan","PK",92],["Bangladesh","BD",880],["China","CN",86],["Japan","JP",81],["South Korea","KR",82],["Indonesia","ID",62],["Philippines","PH",63],["Vietnam","VN",84],["Thailand","TH",66],["Malaysia","MY",60],["Singapore","SG",65],["Australia","AU",61],["New Zealand","NZ",64]];
export const COUNTRIES = C.map(([name, iso, dial]) => ({ name, iso, dial: String(dial), flag: [...iso].map((ch) => String.fromCodePoint(127397 + ch.charCodeAt(0))).join("") }));

function guessIso() { try { return (navigator.language || "").split("-")[1]?.toUpperCase() || ""; } catch { return ""; } }

/** HTML for the field. `id` is a prefix: #<id>-cc (select) and #<id>-num (input). */
export function phoneFieldHtml(id, e164 = "") {
  const sel = splitPhone(e164) || { dial: COUNTRIES.find((c) => c.iso === guessIso())?.dial || "", number: "" };
  const opts = COUNTRIES.map((c) => `<option value="${c.dial}" data-iso="${c.iso}"${c.dial === sel.dial && c.iso === (sel.iso || c.iso) ? " selected" : ""}>${c.flag} ${esc(c.name)} (+${c.dial})</option>`).join("");
  return html`<div class="phone-row"><select class="select" id="${id}-cc" aria-label="Country code">${raw(`<option value="">Country…</option>${opts}<option value="other"${sel.dial === "other" ? " selected" : ""}>Other (type full +number)</option>`)}</select>
    <input class="input" id="${id}-num" type="tel" inputmode="tel" autocomplete="tel-national" maxlength="24" value="${sel.number}" placeholder="Phone number"></div>`;
}

export function splitPhone(e164) {
  if (!e164) return null;
  const digits = String(e164).replace(/[^\d+]/g, "");
  if (!digits.startsWith("+")) return { dial: "other", number: e164 };
  const rest = digits.slice(1);
  const match = [...COUNTRIES].sort((a, b) => b.dial.length - a.dial.length).find((c) => rest.startsWith(c.dial));
  return match ? { dial: match.dial, iso: match.iso === "CA" ? "US" : match.iso, number: rest.slice(match.dial.length) } : { dial: "other", number: digits };
}

/** Returns { value: "+234…" | null, error: string | null }. Empty input is valid (value null). */
export function readPhone(root, id) {
  const cc = root.querySelector(`#${id}-cc`).value, raw0 = root.querySelector(`#${id}-num`).value.trim();
  if (!raw0) return { value: null, error: null };
  let digits = raw0.replace(/[^\d]/g, "");
  let full;
  if (cc === "other" || raw0.startsWith("+")) full = "+" + digits;
  else if (!cc) return { value: null, error: "Choose your country code." };
  else full = "+" + cc + digits.replace(/^0+/, "");
  return /^\+[1-9]\d{6,14}$/.test(full) ? { value: full, error: null } : { value: null, error: "Enter a valid phone number (7–15 digits)." };
}
