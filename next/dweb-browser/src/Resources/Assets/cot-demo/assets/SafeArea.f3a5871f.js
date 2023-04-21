import { d as defineComponent, r as ref, o as onMounted, c as createElementBlock, a as createVNode, b as createBaseVNode, w as withDirectives, e as vModelCheckbox, u as unref, F as Fragment, f as resolveComponent, g as openBlock } from "./index.f89085c2.js";
import { _ as _imports_0 } from "./safearea.d6166183.js";
import { t as toConsole, _ as _sfc_main$1, d as defineLogAction } from "./LogPanel.fb338927.js";
var SafeArea_vue_vue_type_style_index_0_lang = "";
const _hoisted_1 = { class: "card glass" };
const _hoisted_2 = { class: "card-body" };
const _hoisted_3 = /* @__PURE__ */ createBaseVNode("h2", { class: "card-title" }, "Safe Area Overlay", -1);
const _hoisted_4 = { class: "justify-end card-actions btn-group" };
const _hoisted_5 = ["disabled"];
const _hoisted_6 = /* @__PURE__ */ createBaseVNode("div", { class: "divider" }, "LOG", -1);
const _sfc_main = /* @__PURE__ */ defineComponent({
  __name: "SafeArea",
  setup(__props) {
    const title = "Safe Area";
    const $logPanel = ref();
    const $safeArea = ref();
    let console;
    let safeArea;
    onMounted(async () => {
      console = toConsole($logPanel);
      safeArea = $safeArea.value;
      onSafeAreaChange(await safeArea.getState(), "init");
    });
    const onSafeAreaChange = (info, type) => {
      overlay.value = info.overlay;
      info.insets.effect({ css_var_name: "safe-area" });
      console.log(type, info);
    };
    const overlay = ref(void 0);
    const setOverlay = defineLogAction(
      async () => {
        await safeArea.setOverlay(overlay.value);
      },
      { name: "setOverlay", args: [], logPanel: $logPanel }
    );
    const getOverlay = defineLogAction(
      async () => {
        await safeArea.getOverlay();
      },
      { name: "getOverlay", args: [], logPanel: $logPanel }
    );
    return (_ctx, _cache) => {
      const _component_dweb_safe_area = resolveComponent("dweb-safe-area");
      return openBlock(), createElementBlock(Fragment, null, [
        createVNode(_component_dweb_safe_area, {
          ref_key: "$safeArea",
          ref: $safeArea,
          onStatechange: _cache[0] || (_cache[0] = ($event) => onSafeAreaChange($event.detail, "change"))
        }, null, 512),
        createBaseVNode("div", _hoisted_1, [
          createBaseVNode("figure", { class: "icon" }, [
            createBaseVNode("img", {
              src: _imports_0,
              alt: title
            })
          ]),
          createBaseVNode("article", _hoisted_2, [
            _hoisted_3,
            withDirectives(createBaseVNode("input", {
              class: "toggle",
              type: "checkbox",
              "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => overlay.value = $event)
            }, null, 512), [
              [vModelCheckbox, overlay.value]
            ]),
            createBaseVNode("div", _hoisted_4, [
              createBaseVNode("button", {
                class: "inline-block rounded-full btn btn-accent",
                disabled: null == overlay.value,
                onClick: _cache[2] || (_cache[2] = (...args) => unref(setOverlay) && unref(setOverlay)(...args))
              }, " Set ", 8, _hoisted_5),
              createBaseVNode("button", {
                class: "inline-block rounded-full btn btn-accent",
                onClick: _cache[3] || (_cache[3] = (...args) => unref(getOverlay) && unref(getOverlay)(...args))
              }, "Get")
            ])
          ])
        ]),
        _hoisted_6,
        createVNode(_sfc_main$1, {
          ref_key: "$logPanel",
          ref: $logPanel
        }, null, 512)
      ], 64);
    };
  }
});
export { _sfc_main as default };
