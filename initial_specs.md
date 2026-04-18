This updated specification integrates the **Architect AI** design language and the specific "Magnatile Set" components identified in your provided UI.

---

## 1. Product Concept: Architect AI
**Architect AI** is an intelligent design tool that transforms natural language prompts into structured, physical-logic-based compositions. Using a curated library of five core Magnatile components, the AI backend acts as a "Digital Architect," calculating the spatial coordinates and rotation required to assemble complex objects.

---

## 2. Detailed UI Element Identification
Based on the **"Library"** screen provided, the following elements constitute the design system:

### A. Global Navigation & Header
* **App Logo & Title:** Top-left aligned. Features the "Architect AI" branding with a minimalist geometric icon.
* **User Profile Avatar:** Top-right circular "JD" badge (indicating user context/session).
* **Bottom Navigation Bar:** High-contrast floating bar containing four destinations:
    * **Chat:** Entry point for the AI construction interface.
    * **Build:** The active canvas for viewing 2D/3D assemblies.
    * **Library (Active):** Component browser (shown in the image).
    * **Gallery:** Saved user "blueprints."

### B. Component Library Cards (Content Area)
Each component is housed in a vertically stacked, rounded-corner card with:
1.  **Preview Window:** A white recessed square containing the SVG rendering of the tile.
2.  **Title:** Bold, dark-brown serif typography (e.g., "Solid Square").
3.  **Description:** Light-weight body text explaining the architectural use case.
4.  **Metadata Tags:** Rounded-pill badges indicating properties (e.g., "Structural," "Translucent," "90° Corner").

### C. The Core Component Set (AI Constraints)
The AI backend is strictly limited to these five identifiers:
* **Solid Square:** Base structural unit (3x3 units).
* **Window Square:** Decorative/translucent variant.
* **Equilateral Triangle:** 60° standard for patterns/domes.
* **Right Triangle:** 90° component for perpendicular joins and stairs.
* **Isosceles Triangle:** "Spire unit" for vertical emphasis and rooftops.

---

## 3. Functional Specifications

### 3.1 The AI Construction Logic (Chat-to-SVG)
The user interacts via the **Chat** tab.
* **Input:** Natural language (e.g., *"Build a stylized tiger using only triangles and solid squares."*)
* **Processing:** The LLM receives the prompt + the metadata of the 5 tiles.
* **Output:** A JSON array representing the object’s assembly:
```json
{
  "object": "Tiger",
  "components": [
    {"tile_id": "solid_square", "x": 0, "y": 0, "rotation": 0, "color": "#A04523"},
    {"tile_id": "right_triangle", "x": 3, "y": 0, "rotation": 90, "color": "#F18D58"}
  ]
}
```

### 3.2 The "Build" Canvas
Once the chat AI generates the code, the app switches to the **Build** tab.
* **Rendering:** Uses a Snap-to-Grid engine. Since Magnatiles are magnetic, the UI should animate tiles "snapping" together based on their geometric edges (e.g., the hypotenuse of a Right Triangle snapping to the edge of a Solid Square).
* **Tactile Physics:** Components should have a slight drop-shadow and inner glow to mimic the high-density plastic and translucent properties shown in the UI.

---

## 4. Technical Architecture for AI Coders

### Frontend (Android / Kotlin)
* **View Layer:** Jetpack Compose. Use `LazyColumn` for the Library and `Box` with `GraphicLayer` for the Canvas.
* **SVG Rendering:** `AndroidSVG` library or `Coil-SVG` to render the 5 core assets dynamically.
* **Animations:** `UpdateTransition` for the "snapping" effect when the AI builds the object.

### Backend (AI Integration)
* **System Prompting:** You must provide the AI with the specific dimensions of the 5 tiles.
    * *Example:* "The Right Triangle has a base of 3 units and a height of 3 units. You must ensure vertices align perfectly with the Solid Square (3x3)."
* **Constraint Engine:** A middleware layer that validates the AI's output to ensure no two tiles overlap in a way that is physically impossible for Magnatiles.

---

## 5. UI/UX Detail Summary Table

| Element | Specification | Visual Style |
| :--- | :--- | :--- |
| **Primary Buttons** | "Get Free Sample Set" | Pill-shaped, white background, bold text. |
| **Typography** | Brand Serif & UI Sans | Dark Brown (#3D1410) for headers. |
| **Color Palette** | Warm Neutrals | Background: #FFF5F2; Accent: #E0542E. |
| **Iconography** | Geometric/Line Art | Minimalist icons in the Bottom Nav. |
| **Interaction** | Magnetic Snapping | Haptic feedback when tiles connect on canvas. |

---

## 6. Next Implementation Steps
1.  **Asset Export:** Create the 5 SVG files based exactly on the visuals in the "Magnatile Set" UI.
2.  **Logic Mapping:** Define the coordinate system (e.g., 1 unit = 10dp) for the AI to understand spatial relationships.
3.  **Chat Interface:** Implement the Chat UI following the same color palette as the "JD" profile header.