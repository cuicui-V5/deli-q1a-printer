/**
 * Deli Q1A Label Studio - Frontend Application Engine
 * Canvas editing, Barcode/QR generation, Templates, Thermal simulation, BLE Printing
 */

document.addEventListener('DOMContentLoaded', () => {
  // ── Geometry & State ────────────────────────────────────────────────
  const DPI = 203;
  const MM_TO_PX = 8; // ~8 dots per mm at 203 DPI (203 / 25.4 ≈ 7.992)

  // Label paper geometry definitions (in mm, 203 DPI = 8 dots/mm)
  const PAPER_PRESETS = {
    '15x30': { widthMm: 15, lengthMm: 30, name: '15 × 30 mm (标准间隙纸)' },
    '15x40': { widthMm: 15, lengthMm: 40, name: '15 × 40 mm (长间隙纸)' },
    '15x50': { widthMm: 15, lengthMm: 50, name: '15 × 50 mm (加大间隙纸)' },
    '12x30': { widthMm: 12, lengthMm: 30, name: '12 × 30 mm (12mm规格)' },
    '12x40': { widthMm: 12, lengthMm: 40, name: '12 × 40 mm (12mm规格)' },
  };

  let currentPresetKey = '15x30';
  let isLandscape = true;
  // 15mm x 30mm label @ 8 dots/mm:
  // Feed direction (length): 30mm x 8 = 240px; Printhead direction (width): 15mm x 8 = 120px
  let canvasW = 240; // 30mm in Landscape
  let canvasH = 120; // 15mm in Landscape
  let zoomLevel = 2.5; // 250% display zoom for comfortable editing

  // Fabric Canvas Instance
  const canvas = new fabric.Canvas('labelCanvas', {
    width: canvasW,
    height: canvasH,
    backgroundColor: '#ffffff',
    selection: true,
    preserveObjectStacking: true
  });
  window.fabricCanvas = canvas;

  // History Stack for Undo / Redo
  const history = [];
  let historyIndex = -1;
  let isHistoryProcessing = false;

  function pushHistory() {
    if (isHistoryProcessing) return;
    const json = JSON.stringify(canvas.toJSON());
    // Truncate future states if we made changes after undo
    if (historyIndex < history.length - 1) {
      history.splice(historyIndex + 1);
    }
    history.push(json);
    historyIndex++;
    updateHistoryButtons();
  }

  function updateHistoryButtons() {
    document.getElementById('btnUndo').disabled = (historyIndex <= 0);
    document.getElementById('btnRedo').disabled = (historyIndex >= history.length - 1);
  }

  function applyZoom() {
    canvas.setWidth(canvasW * zoomLevel);
    canvas.setHeight(canvasH * zoomLevel);
    canvas.setZoom(zoomLevel);
    canvas.renderAll();
    document.getElementById('zoomLevelText').innerText = `${Math.round(zoomLevel * 100)}%`;
  }

  function updateDimensions() {
    const preset = PAPER_PRESETS[currentPresetKey] || PAPER_PRESETS['15x30'];
    const wPx = preset.widthMm * MM_TO_PX;   // e.g. 15 * 8 = 120
    const lPx = preset.lengthMm * MM_TO_PX;  // e.g. 30 * 8 = 240

    if (isLandscape) {
      canvasW = lPx;
      canvasH = wPx;
      const ruler = document.getElementById('rulerTag');
      if (ruler) ruler.innerText = `${preset.lengthMm} × ${preset.widthMm} mm`;
    } else {
      canvasW = wPx;
      canvasH = lPx;
      const ruler = document.getElementById('rulerTag');
      if (ruler) ruler.innerText = `${preset.widthMm} × ${preset.lengthMm} mm`;
    }
    applyZoom();
  }

  function setOrientation(landscape) {
    isLandscape = landscape;
    updateDimensions();
    canvas.clear();
    canvas.backgroundColor = '#ffffff';
    pushHistory();
    const preset = PAPER_PRESETS[currentPresetKey] || PAPER_PRESETS['15x30'];
    showToast(isLandscape ? `已切换为横向模式 (${preset.lengthMm}×${preset.widthMm}mm)` : `已切换为纵向模式 (${preset.widthMm}×${preset.lengthMm}mm)`, 'info');
  }

  // Paper Size Selector
  const paperSelect = document.getElementById('paperSizeSelect');
  if (paperSelect) {
    paperSelect.addEventListener('change', (e) => {
      currentPresetKey = e.target.value;
      updateDimensions();
      canvas.clear();
      canvas.backgroundColor = '#ffffff';
      pushHistory();
      showToast(`已切换规格: ${PAPER_PRESETS[currentPresetKey].name}`, 'info');
    });
  }

  // ── Toolbox Navigation ──────────────────────────────────────────────
  const tabButtons = document.querySelectorAll('.toolbox-tabs .tab-item');
  const tabPanels = document.querySelectorAll('.toolbox-content .tab-panel');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      tabPanels.forEach(p => p.classList.remove('active'));

      btn.classList.add('active');
      const targetId = btn.getAttribute('data-tab');
      document.getElementById(targetId).classList.add('active');
    });
  });

  // ── Zoom & History Event Listeners ──────────────────────────────────
  document.getElementById('btnZoomIn').addEventListener('click', () => {
    zoomLevel = Math.min(5.0, zoomLevel + 0.5);
    applyZoom();
  });

  document.getElementById('btnZoomOut').addEventListener('click', () => {
    zoomLevel = Math.max(1.0, zoomLevel - 0.5);
    applyZoom();
  });

  document.getElementById('btnZoomFit').addEventListener('click', () => {
    zoomLevel = 3.0;
    applyZoom();
  });

  document.getElementById('btnRotateCanvas').addEventListener('click', () => {
    setOrientation(!isLandscape);
  });

  document.getElementById('btnUndo').addEventListener('click', () => {
    if (historyIndex > 0) {
      isHistoryProcessing = true;
      historyIndex--;
      canvas.loadFromJSON(history[historyIndex], () => {
        canvas.renderAll();
        isHistoryProcessing = false;
        updateHistoryButtons();
      });
    }
  });

  document.getElementById('btnRedo').addEventListener('click', () => {
    if (historyIndex < history.length - 1) {
      isHistoryProcessing = true;
      historyIndex++;
      canvas.loadFromJSON(history[historyIndex], () => {
        canvas.renderAll();
        isHistoryProcessing = false;
        updateHistoryButtons();
      });
    }
  });

  document.getElementById('btnClear').addEventListener('click', () => {
    if (confirm('确认清空画板上的所有内容吗？')) {
      canvas.clear();
      canvas.backgroundColor = '#ffffff';
      canvas.renderAll();
      pushHistory();
    }
  });

  // ── Adding Text Elements ────────────────────────────────────────────
  function addText(text, fontSize = 18, isBold = false) {
    const t = new fabric.IText(text, {
      left: canvasW / 2,
      top: canvasH / 2,
      originX: 'center',
      originY: 'center',
      fontSize: fontSize,
      fontFamily: "'Microsoft YaHei', sans-serif",
      fontWeight: isBold ? 'bold' : 'normal',
      fill: '#000000'
    });
    canvas.add(t);
    canvas.setActiveObject(t);
    canvas.renderAll();
    pushHistory();
  }

  document.getElementById('btnAddHeading').addEventListener('click', () => addText('标题文字', 20, true));
  document.getElementById('btnAddSubtitle').addEventListener('click', () => addText('副标题规格', 14, false));
  document.getElementById('btnAddBody').addEventListener('click', () => addText('备注说明文字', 11, false));
  document.getElementById('btnAddMultiLine').addEventListener('click', () => addText('第一行项目\n第二行项目', 13, false));

  // ── Adding Barcode & QR Code ────────────────────────────────────────
  document.getElementById('btnAddQrCode').addEventListener('click', () => {
    const text = document.getElementById('qrInputText').value.trim() || 'https://deli.com';
    const tempDiv = document.createElement('div');
    new QRCode(tempDiv, {
      text: text,
      width: 64,
      height: 64,
      colorDark: '#000000',
      colorLight: '#ffffff',
      correctLevel: QRCode.CorrectLevel.M
    });

    setTimeout(() => {
      const qrCanvas = tempDiv.querySelector('canvas');
      const qrImg = tempDiv.querySelector('img');
      const dataUrl = qrCanvas ? qrCanvas.toDataURL() : (qrImg && qrImg.src ? qrImg.src : null);
      if (dataUrl) {
        fabric.Image.fromURL(dataUrl, (img) => {
          img.set({
            left: canvasW / 2,
            top: canvasH / 2,
            originX: 'center',
            originY: 'center',
            scaleX: 0.8,
            scaleY: 0.8
          });
          canvas.add(img);
          canvas.setActiveObject(img);
          canvas.renderAll();
          pushHistory();
        });
      }
    }, 80);
  });

  document.getElementById('btnAddBarcode').addEventListener('click', () => {
    const text = document.getElementById('barcodeInputText').value.trim() || 'DELI-2026';
    const showText = document.getElementById('barcodeShowText').checked;

    const bcCanvas = document.createElement('canvas');
    try {
      JsBarcode(bcCanvas, text, {
        format: 'CODE128',
        displayValue: showText,
        fontSize: 10,
        height: 24,
        margin: 2
      });

      fabric.Image.fromURL(bcCanvas.toDataURL(), (img) => {
        img.set({
          left: canvasW / 2,
          top: canvasH / 2,
          originX: 'center',
          originY: 'center',
          scaleX: 0.85,
          scaleY: 0.85
        });
        canvas.add(img);
        canvas.setActiveObject(img);
        canvas.renderAll();
        pushHistory();
      });
    } catch (e) {
      showToast('条形码生成失败，请检查输入内容', 'error');
    }
  });

  // ── Adding Shapes & Lines ───────────────────────────────────────────
  document.getElementById('btnAddRect').addEventListener('click', () => {
    const rect = new fabric.Rect({
      left: canvasW / 2,
      top: canvasH / 2,
      originX: 'center',
      originY: 'center',
      width: 60,
      height: 36,
      fill: 'transparent',
      stroke: '#000000',
      strokeWidth: 2
    });
    canvas.add(rect);
    canvas.setActiveObject(rect);
    canvas.renderAll();
    pushHistory();
  });

  document.getElementById('btnAddRoundRect').addEventListener('click', () => {
    const rect = new fabric.Rect({
      left: canvasW / 2,
      top: canvasH / 2,
      originX: 'center',
      originY: 'center',
      width: 70,
      height: 40,
      rx: 6,
      ry: 6,
      fill: 'transparent',
      stroke: '#000000',
      strokeWidth: 2
    });
    canvas.add(rect);
    canvas.setActiveObject(rect);
    canvas.renderAll();
    pushHistory();
  });

  document.getElementById('btnAddCircle').addEventListener('click', () => {
    const circle = new fabric.Circle({
      left: canvasW / 2,
      top: canvasH / 2,
      originX: 'center',
      originY: 'center',
      radius: 20,
      fill: 'transparent',
      stroke: '#000000',
      strokeWidth: 2
    });
    canvas.add(circle);
    canvas.setActiveObject(circle);
    canvas.renderAll();
    pushHistory();
  });

  document.getElementById('btnAddLineH').addEventListener('click', () => {
    const line = new fabric.Line([10, canvasH / 2, canvasW - 10, canvasH / 2], {
      stroke: '#000000',
      strokeWidth: 1
    });
    canvas.add(line);
    canvas.setActiveObject(line);
    canvas.renderAll();
    pushHistory();
  });

  document.getElementById('btnAddDashedLine').addEventListener('click', () => {
    const line = new fabric.Line([10, canvasH / 2, canvasW - 10, canvasH / 2], {
      stroke: '#000000',
      strokeWidth: 1,
      strokeDashArray: [4, 4]
    });
    canvas.add(line);
    canvas.setActiveObject(line);
    canvas.renderAll();
    pushHistory();
  });

  document.getElementById('btnAddOuterBorder').addEventListener('click', () => {
    const border = new fabric.Rect({
      left: 1,
      top: 1,
      width: canvasW - 3,
      height: canvasH - 3,
      fill: 'transparent',
      stroke: '#000000',
      strokeWidth: 1,
      selectable: true
    });
    canvas.add(border);
    canvas.sendToBack(border);
    canvas.renderAll();
    pushHistory();
  });

  // ── Adding Icons & Symbols ──────────────────────────────────────────
  document.querySelectorAll('.icon-cell').forEach(cell => {
    cell.addEventListener('click', () => {
      const char = cell.innerText;
      const iconText = new fabric.IText(char, {
        left: canvasW / 2,
        top: canvasH / 2,
        originX: 'center',
        originY: 'center',
        fontSize: 28,
        fill: '#000000'
      });
      canvas.add(iconText);
      canvas.setActiveObject(iconText);
      canvas.renderAll();
      pushHistory();
    });
  });

  // ── Image Upload Handling ───────────────────────────────────────────
  const dropzone = document.getElementById('dropzone');
  const imageFileInput = document.getElementById('imageFileInput');

  dropzone.addEventListener('click', () => imageFileInput.click());

  dropzone.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropzone.style.borderColor = 'var(--brand-color)';
  });

  dropzone.addEventListener('dragleave', () => {
    dropzone.style.borderColor = '#cbd5e1';
  });

  dropzone.addEventListener('drop', (e) => {
    e.preventDefault();
    dropzone.style.borderColor = '#cbd5e1';
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleImageFile(e.dataTransfer.files[0]);
    }
  });

  imageFileInput.addEventListener('change', (e) => {
    if (e.target.files && e.target.files[0]) {
      handleImageFile(e.target.files[0]);
    }
  });

  function handleImageFile(file) {
    const reader = new FileReader();
    reader.onload = (f) => {
      fabric.Image.fromURL(f.target.result, (img) => {
        // Auto scale to fit comfortably inside canvas
        const maxW = canvasW * 0.7;
        const maxH = canvasH * 0.7;
        const scale = Math.min(maxW / img.width, maxH / img.height, 1);

        img.set({
          left: canvasW / 2,
          top: canvasH / 2,
          originX: 'center',
          originY: 'center',
          scaleX: scale,
          scaleY: scale
        });
        canvas.add(img);
        canvas.setActiveObject(img);
        canvas.renderAll();
        pushHistory();
        showToast('图片添加成功', 'success');
      });
    };
    reader.readAsDataURL(file);
  }

  // ── Pre-made Templates ──────────────────────────────────────────────
  const templates = {
    storage: () => {
      canvas.clear();
      canvas.backgroundColor = '#ffffff';

      // Outer Border (safe margin matching rounded die-cut sticker)
      canvas.add(new fabric.Rect({
        left: 4, top: 4, width: canvasW - 8, height: canvasH - 8,
        rx: 6, ry: 6, fill: 'transparent', stroke: '#000', strokeWidth: 1
      }));
      // Title
      canvas.add(new fabric.IText('居家整理收纳', {
        left: canvasW / 2, top: 14, originX: 'center', originY: 'top',
        fontSize: 17, fontWeight: 'bold', fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      // Divider
      canvas.add(new fabric.Line([14, 42, canvasW - 14, 42], { stroke: '#000', strokeWidth: 1 }));
      // Subtitle
      canvas.add(new fabric.IText('物品: 数据线 / 扩展坞', {
        left: canvasW / 2, top: 54, originX: 'center', originY: 'top',
        fontSize: 13, fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      // Subtext
      canvas.add(new fabric.IText('2026-11-03 整理于书房', {
        left: canvasW / 2, top: 84, originX: 'center', originY: 'top',
        fontSize: 11, fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.renderAll();
      pushHistory();
    },

    cable: () => {
      canvas.clear();
      canvas.backgroundColor = '#ffffff';

      canvas.add(new fabric.Rect({
        left: 4, top: 4, width: canvasW - 8, height: canvasH - 8,
        rx: 6, ry: 6, fill: 'transparent', stroke: '#000', strokeWidth: 1
      }));
      canvas.add(new fabric.IText('🔌 Type-C 快充线', {
        left: 16, top: 14, originX: 'left', originY: 'top',
        fontSize: 16, fontWeight: 'bold', fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.add(new fabric.Line([14, 42, canvasW - 14, 42], { stroke: '#000', strokeWidth: 1 }));
      canvas.add(new fabric.IText('规格: 100W PD 快充', {
        left: 16, top: 54, originX: 'left', originY: 'top',
        fontSize: 13, fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.add(new fabric.IText('位置: 桌面主显示器', {
        left: 16, top: 84, originX: 'left', originY: 'top',
        fontSize: 12, fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.renderAll();
      pushHistory();
    },

    food: () => {
      canvas.clear();
      canvas.backgroundColor = '#ffffff';

      canvas.add(new fabric.Rect({
        left: 4, top: 4, width: canvasW - 8, height: canvasH - 8,
        rx: 6, ry: 6, fill: 'transparent', stroke: '#000', strokeWidth: 1
      }));
      canvas.add(new fabric.IText('🍎 食品赏味保鲜标', {
        left: canvasW / 2, top: 14, originX: 'center', originY: 'top',
        fontSize: 16, fontWeight: 'bold', fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.add(new fabric.Line([14, 42, canvasW - 14, 42], { stroke: '#000', strokeWidth: 1 }));
      canvas.add(new fabric.IText('开封: 2026-11-03 12:00', {
        left: 18, top: 54, originX: 'left', originY: 'top',
        fontSize: 13, fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.add(new fabric.IText('保质: 7天内冷藏食用', {
        left: 18, top: 84, originX: 'left', originY: 'top',
        fontSize: 13, fontWeight: 'bold', fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.renderAll();
      pushHistory();
    },

    price: () => {
      canvas.clear();
      canvas.backgroundColor = '#ffffff';

      canvas.add(new fabric.Rect({
        left: 4, top: 4, width: canvasW - 8, height: canvasH - 8,
        rx: 6, ry: 6, fill: 'transparent', stroke: '#000', strokeWidth: 1
      }));
      canvas.add(new fabric.IText('特惠尝鲜价', {
        left: 16, top: 14, originX: 'left', originY: 'top',
        fontSize: 15, fontWeight: 'bold', fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.add(new fabric.IText('¥ 19.90', {
        left: 16, top: 42, originX: 'left', originY: 'top',
        fontSize: 26, fontWeight: 'bold', fontFamily: "'Microsoft YaHei', sans-serif"
      }));
      canvas.add(new fabric.IText('原价: ¥29.90', {
        left: 16, top: 84, originX: 'left', originY: 'top',
        fontSize: 11, fontFamily: "'Microsoft YaHei', sans-serif"
      }));

      // Sample barcode on right
      const bcCanvas = document.createElement('canvas');
      JsBarcode(bcCanvas, '199026', { format: 'CODE128', displayValue: false, height: 48, margin: 0 });
      fabric.Image.fromURL(bcCanvas.toDataURL(), (img) => {
        img.set({ left: canvasW - 16, top: canvasH / 2, originX: 'right', originY: 'center', scaleX: 0.9, scaleY: 0.9 });
        canvas.add(img);
        canvas.renderAll();
        pushHistory();
      });
    }
  };

  document.querySelectorAll('.template-card').forEach(card => {
    card.addEventListener('click', () => {
      const tplName = card.getAttribute('data-template');
      if (templates[tplName]) {
        templates[tplName]();
        showToast('模版应用成功', 'success');
      }
    });
  });

  // ── Inspector & Selection Sync ──────────────────────────────────────
  const noSelectionHint = document.getElementById('noSelectionHint');
  const selectionControls = document.getElementById('selectionControls');
  const textPropsSection = document.getElementById('textPropsSection');
  const shapePropsSection = document.getElementById('shapePropsSection');

  const propTextContent = document.getElementById('propTextContent');
  const propFontFamily = document.getElementById('propFontFamily');
  const propFontSize = document.getElementById('propFontSize');
  const propBoldBtn = document.getElementById('propBoldBtn');
  const propItalicBtn = document.getElementById('propItalicBtn');
  const propUnderlineBtn = document.getElementById('propUnderlineBtn');
  const propAlignLeft = document.getElementById('propAlignLeft');
  const propAlignCenter = document.getElementById('propAlignCenter');
  const propAlignRight = document.getElementById('propAlignRight');
  const propStrokeWidth = document.getElementById('propStrokeWidth');
  const propCornerRadius = document.getElementById('propCornerRadius');
  const cornerRadiusCol = document.getElementById('cornerRadiusCol');

  canvas.on('selection:created', updateInspector);
  canvas.on('selection:updated', updateInspector);
  canvas.on('selection:cleared', () => {
    noSelectionHint.style.display = 'flex';
    selectionControls.style.display = 'none';
  });

  canvas.on('object:modified', pushHistory);

  function updateInspector() {
    const active = canvas.getActiveObject();
    if (!active) {
      noSelectionHint.style.display = 'flex';
      selectionControls.style.display = 'none';
      return;
    }

    noSelectionHint.style.display = 'none';
    selectionControls.style.display = 'block';

    // Handle Text Objects
    if (active.type === 'i-text' || active.type === 'text') {
      textPropsSection.style.display = 'block';
      shapePropsSection.style.display = 'none';

      propTextContent.value = active.text;
      propFontFamily.value = active.fontFamily || "'Microsoft YaHei', sans-serif";
      propFontSize.value = active.fontSize;

      propBoldBtn.classList.toggle('active', active.fontWeight === 'bold');
      propItalicBtn.classList.toggle('active', active.fontStyle === 'italic');
      propUnderlineBtn.classList.toggle('active', !!active.underline);

      propAlignLeft.classList.toggle('active', active.textAlign === 'left');
      propAlignCenter.classList.toggle('active', active.textAlign === 'center');
      propAlignRight.classList.toggle('active', active.textAlign === 'right');
    }
    // Handle Shapes (Rect, Circle, Line)
    else if (active.type === 'rect' || active.type === 'circle' || active.type === 'line') {
      textPropsSection.style.display = 'none';
      shapePropsSection.style.display = 'block';

      propStrokeWidth.value = active.strokeWidth || 1;
      if (active.type === 'rect') {
        cornerRadiusCol.style.display = 'block';
        propCornerRadius.value = active.rx || 0;
      } else {
        cornerRadiusCol.style.display = 'none';
      }
    } else {
      textPropsSection.style.display = 'none';
      shapePropsSection.style.display = 'none';
    }
  }

  // Text property change handlers
  propTextContent.addEventListener('input', () => {
    const active = canvas.getActiveObject();
    if (active && (active.type === 'i-text' || active.type === 'text')) {
      active.set('text', propTextContent.value);
      canvas.renderAll();
      pushHistory();
    }
  });

  propFontFamily.addEventListener('change', () => {
    const active = canvas.getActiveObject();
    if (active) {
      active.set('fontFamily', propFontFamily.value);
      canvas.renderAll();
      pushHistory();
    }
  });

  propFontSize.addEventListener('change', () => {
    const active = canvas.getActiveObject();
    if (active) {
      active.set('fontSize', parseInt(propFontSize.value, 10));
      canvas.renderAll();
      pushHistory();
    }
  });

  propBoldBtn.addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      const isBold = active.fontWeight === 'bold';
      active.set('fontWeight', isBold ? 'normal' : 'bold');
      propBoldBtn.classList.toggle('active', !isBold);
      canvas.renderAll();
      pushHistory();
    }
  });

  propItalicBtn.addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      const isItalic = active.fontStyle === 'italic';
      active.set('fontStyle', isItalic ? 'normal' : 'italic');
      propItalicBtn.classList.toggle('active', !isItalic);
      canvas.renderAll();
      pushHistory();
    }
  });

  propUnderlineBtn.addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      active.set('underline', !active.underline);
      propUnderlineBtn.classList.toggle('active', active.underline);
      canvas.renderAll();
      pushHistory();
    }
  });

  [propAlignLeft, propAlignCenter, propAlignRight].forEach((btn, idx) => {
    btn.addEventListener('click', () => {
      const active = canvas.getActiveObject();
      if (active) {
        const align = ['left', 'center', 'right'][idx];
        active.set('textAlign', align);
        propAlignLeft.classList.toggle('active', align === 'left');
        propAlignCenter.classList.toggle('active', align === 'center');
        propAlignRight.classList.toggle('active', align === 'right');
        canvas.renderAll();
        pushHistory();
      }
    });
  });

  // Shape property change handlers
  propStrokeWidth.addEventListener('change', () => {
    const active = canvas.getActiveObject();
    if (active) {
      active.set('strokeWidth', parseInt(propStrokeWidth.value, 10));
      canvas.renderAll();
      pushHistory();
    }
  });

  propCornerRadius.addEventListener('change', () => {
    const active = canvas.getActiveObject();
    if (active && active.type === 'rect') {
      const r = parseInt(propCornerRadius.value, 10);
      active.set({ rx: r, ry: r });
      canvas.renderAll();
      pushHistory();
    }
  });

  // Alignment & Object Actions
  document.getElementById('btnCenterHoriz').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      canvas.centerObjectH(active);
      canvas.renderAll();
      pushHistory();
    }
  });

  document.getElementById('btnCenterVert').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      canvas.centerObjectV(active);
      canvas.renderAll();
      pushHistory();
    }
  });

  document.getElementById('btnRotate90').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      active.rotate(((active.angle || 0) + 90) % 360);
      canvas.renderAll();
      pushHistory();
    }
  });

  document.getElementById('btnBringForward').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      canvas.bringForward(active);
      canvas.renderAll();
      pushHistory();
    }
  });

  document.getElementById('btnSendBackward').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      canvas.sendBackwards(active);
      canvas.renderAll();
      pushHistory();
    }
  });

  document.getElementById('btnDuplicate').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      active.clone((cloned) => {
        cloned.set({
          left: cloned.left + 8,
          top: cloned.top + 8
        });
        canvas.add(cloned);
        canvas.setActiveObject(cloned);
        canvas.renderAll();
        pushHistory();
      });
    }
  });

  document.getElementById('btnDeleteObj').addEventListener('click', () => {
    const active = canvas.getActiveObject();
    if (active) {
      canvas.remove(active);
      canvas.discardActiveObject();
      canvas.renderAll();
      pushHistory();
    }
  });

  // Keyboard Shortcuts
  window.addEventListener('keydown', (e) => {
    // If typing in input or textarea, don't trigger global shortcuts
    if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

    const active = canvas.getActiveObject();
    if (e.key === 'Delete' || e.key === 'Backspace') {
      if (active) {
        canvas.remove(active);
        canvas.discardActiveObject();
        canvas.renderAll();
        pushHistory();
      }
    } else if (e.key === 'ArrowUp' && active) {
      active.top -= e.shiftKey ? 10 : 1;
      canvas.renderAll();
    } else if (e.key === 'ArrowDown' && active) {
      active.top += e.shiftKey ? 10 : 1;
      canvas.renderAll();
    } else if (e.key === 'ArrowLeft' && active) {
      active.left -= e.shiftKey ? 10 : 1;
      canvas.renderAll();
    } else if (e.key === 'ArrowRight' && active) {
      active.left += e.shiftKey ? 10 : 1;
      canvas.renderAll();
    } else if ((e.ctrlKey || e.metaKey) && e.key === 'z') {
      e.preventDefault();
      document.getElementById('btnUndo').click();
    } else if ((e.ctrlKey || e.metaKey) && e.key === 'y') {
      e.preventDefault();
      document.getElementById('btnRedo').click();
    }
  });

  // ── JSON Project Import & Export ─────────────────────────────────────
  document.getElementById('btnExportJson').addEventListener('click', () => {
    const json = JSON.stringify(canvas.toJSON(), null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `deli_label_${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    showToast('工程文件已保存', 'success');
  });

  const jsonFileInput = document.getElementById('jsonFileInput');
  document.getElementById('btnImportJson').addEventListener('click', () => jsonFileInput.click());

  jsonFileInput.addEventListener('change', (e) => {
    if (e.target.files && e.target.files[0]) {
      const reader = new FileReader();
      reader.onload = (f) => {
        try {
          canvas.loadFromJSON(f.target.result, () => {
            canvas.renderAll();
            pushHistory();
            showToast('工程加载成功', 'success');
          });
        } catch (err) {
          showToast('JSON 文件格式错误', 'error');
        }
      };
      reader.readAsText(e.target.files[0]);
    }
  });

  // ── PNG Export ───────────────────────────────────────────────────────
  document.getElementById('btnExportPng').addEventListener('click', () => {
    // Generate at 1:1 scale (no zoom factor)
    const dataUrl = canvas.toDataURL({
      format: 'png',
      multiplier: 1 / zoomLevel
    });
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = `label_${canvasW}x${canvasH}.png`;
    a.click();
    showToast('高清 PNG 已导出', 'success');
  });

  // ── Thermal Dither Preview Toggle ───────────────────────────────────
  let isDitherPreview = false;
  let savedJsonBeforePreview = null;

  document.getElementById('btnPreviewToggle').addEventListener('click', () => {
    isDitherPreview = !isDitherPreview;
    const btn = document.getElementById('btnPreviewToggle');
    btn.classList.toggle('active', isDitherPreview);

    if (isDitherPreview) {
      savedJsonBeforePreview = JSON.stringify(canvas.toJSON());
      // Render canvas to 1-bit image and show preview
      const dataUrl = canvas.toDataURL({ format: 'png', multiplier: 1 / zoomLevel });
      const img = new Image();
      img.onload = () => {
        const offCanvas = document.createElement('canvas');
        offCanvas.width = canvasW;
        offCanvas.height = canvasH;
        const ctx = offCanvas.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvasW, canvasH);
        ctx.drawImage(img, 0, 0);

        const imgData = ctx.getImageData(0, 0, canvasW, canvasH);
        const d = imgData.data;
        // Simple threshold
        for (let i = 0; i < d.length; i += 4) {
          const lum = 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];
          const val = lum < 128 ? 0 : 255;
          d[i] = val;
          d[i + 1] = val;
          d[i + 2] = val;
        }
        ctx.putImageData(imgData, 0, 0);

        canvas.clear();
        fabric.Image.fromURL(offCanvas.toDataURL(), (fabImg) => {
          fabImg.set({ selectable: false, evented: false });
          canvas.add(fabImg);
          canvas.renderAll();
        });
      };
      img.src = dataUrl;
      showToast('热敏点阵模拟已开启', 'info');
    } else {
      if (savedJsonBeforePreview) {
        canvas.loadFromJSON(savedJsonBeforePreview, () => {
          canvas.renderAll();
          showToast('恢复正常编辑模式', 'info');
        });
      }
    }
  });

  // ── Copies Stepper ──────────────────────────────────────────────────
  const copiesInput = document.getElementById('copiesInput');
  document.getElementById('btnCopiesMinus').addEventListener('click', () => {
    let val = parseInt(copiesInput.value, 10) || 1;
    if (val > 1) copiesInput.value = val - 1;
  });

  document.getElementById('btnCopiesPlus').addEventListener('click', () => {
    let val = parseInt(copiesInput.value, 10) || 1;
    if (val < 20) copiesInput.value = val + 1;
  });

  // ── Scan Bluetooth Printers ─────────────────────────────────────────
  const btnResetMac = document.getElementById('btnResetMac');
  if (btnResetMac) {
    btnResetMac.addEventListener('click', () => {
      document.getElementById('printerMacInput').value = '5E:55:09:F0:A2:1E';
      showToast('已恢复默认 MAC 地址: 5E:55:09:F0:A2:1E', 'info');
    });
  }

  const btnScanBle = document.getElementById('btnScanBle');
  btnScanBle.addEventListener('click', async () => {
    btnScanBle.disabled = true;
    btnScanBle.innerText = '扫描中...';
    try {
      const resp = await fetch('/api/scan');
      const data = await resp.json();
      if (data.devices && data.devices.length > 0) {
        const deliDev = data.devices.find(d => d.is_deli);
        if (deliDev) {
          document.getElementById('printerMacInput').value = deliDev.address;
          showToast(`已成功匹配到得力打印机: ${deliDev.name} [${deliDev.address}]`, 'success');
        } else {
          // Do NOT overwrite with random unrelated Bluetooth devices!
          showToast(`未扫描到得力打印机，请确保打印机已开机（蓝灯常亮）。已保留默认 MAC`, 'error');
        }
      } else {
        showToast('未扫描到蓝牙设备，请确认打印机已开机', 'error');
      }
    } catch (err) {
      showToast('蓝牙扫描请求失败: ' + err.message, 'error');
    } finally {
      btnScanBle.disabled = false;
      btnScanBle.innerHTML = `
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path></svg>
        <span>扫描</span>
      `;
    }
  });

  // ── Print Execution ─────────────────────────────────────────────────
  const btnExecutePrint = document.getElementById('btnExecutePrint');
  const printStatusMsg = document.getElementById('printStatusMsg');

  btnExecutePrint.addEventListener('click', async () => {
    // If in dither preview, restore before capturing clean image
    if (isDitherPreview && savedJsonBeforePreview) {
      document.getElementById('btnPreviewToggle').click();
      await new Promise(r => setTimeout(r, 100));
    }

    // Capture clean 1:1 image
    canvas.discardActiveObject();
    canvas.renderAll();

    const dataUrl = canvas.toDataURL({
      format: 'png',
      multiplier: 1 / zoomLevel
    });

    const mac = document.getElementById('printerMacInput').value.trim();
    const density = parseInt(document.getElementById('printDensitySelect').value, 10);
    const copies = parseInt(document.getElementById('copiesInput').value, 10) || 1;

    btnExecutePrint.disabled = true;
    btnExecutePrint.classList.add('printing');
    document.getElementById('printBtnText').innerText = '正在连接与打印...';
    printStatusMsg.className = 'print-status-msg active';
    printStatusMsg.innerText = '正在通过 BLE 蓝牙发送指令至得力 Q1A...';

    try {
      const resp = await fetch('/api/print', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          image_base64: dataUrl,
          mac: mac,
          density: density,
          copies: copies,
          width: canvasW,
          height: canvasH
        })
      });

      const resData = await resp.json();
      if (!resp.ok) {
        throw new Error(resData.detail || '打印服务异常');
      }

      printStatusMsg.className = 'print-status-msg active success';
      printStatusMsg.innerText = resData.message || '打印成功完成！';
      showToast(resData.message || '打印成功！', 'success');
    } catch (err) {
      printStatusMsg.className = 'print-status-msg active error';
      printStatusMsg.innerText = '错误: ' + err.message;
      showToast('打印失败: ' + err.message, 'error');
    } finally {
      btnExecutePrint.disabled = false;
      btnExecutePrint.classList.remove('printing');
      document.getElementById('printBtnText').innerText = '立即打印至得力 Q1A';
    }
  });

  // ── Quick Test Print ───────────────────────────────────────────────
  const btnQuickTestPrint = document.getElementById('btnQuickTestPrint');
  if (btnQuickTestPrint) {
    btnQuickTestPrint.addEventListener('click', async () => {
      btnQuickTestPrint.disabled = true;
      showToast('正在向得力 Q1A 发送自检测试页...', 'info');
      try {
        const resp = await fetch('/api/test_print', { method: 'POST' });
        const data = await resp.json();
        if (!resp.ok) throw new Error(data.detail || '自检测试打印失败');
        showToast(data.message || '自检测试页打印完成！', 'success');
      } catch (err) {
        showToast('自检测试失败: ' + err.message, 'error');
      } finally {
        btnQuickTestPrint.disabled = false;
      }
    });
  }

  // ── Toast Notifications ─────────────────────────────────────────────
  function showToast(msg, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerText = msg;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.2s ease';
      setTimeout(() => toast.remove(), 200);
    }, 2800);
  }

  // Initial setup: Load default storage template
  templates.storage();
  applyZoom();

  // Fetch initial configuration from server
  fetch('/api/config')
    .then(r => r.json())
    .then(cfg => {
      if (cfg && cfg.default_mac) {
        const macInput = document.getElementById('printerMacInput');
        if (macInput && !macInput.value) {
          macInput.value = cfg.default_mac;
        }
      }
    })
    .catch(() => {});
});
