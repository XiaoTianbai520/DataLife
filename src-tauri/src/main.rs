#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{
    Emitter, LogicalSize, Manager, PhysicalPosition, WebviewUrl, WebviewWindow,
    WebviewWindowBuilder, WindowEvent,
};

const LABEL: &str = "companion";
const TITLE: &str = "数字生命伴侣";
const BUBBLE_WIDTH: f64 = 376.0;
const BUBBLE_HEIGHT: f64 = 432.0;

fn position_bubble(window: &WebviewWindow) -> tauri::Result<()> {
    if let Some(monitor) = window.current_monitor()?.or(window.primary_monitor()?) {
        let work_area = monitor.work_area();
        let size = window.outer_size()?;
        let margin = (16.0 * monitor.scale_factor()).round() as i32;
        let x = (work_area.size.width as i32 - size.width as i32 - margin).max(0);
        let y = (work_area.size.height as i32 - size.height as i32 - margin).max(0);
        window.set_position(PhysicalPosition::new(
            work_area.position.x + x,
            work_area.position.y + y,
        ))?;
    }
    Ok(())
}

fn apply_mode(window: &WebviewWindow, bubble: bool) -> tauri::Result<()> {
    show(window)?;
    if window.is_maximized()? {
        window.unmaximize()?;
    }
    window.set_decorations(!bubble)?;
    window.set_always_on_top(bubble)?;
    window.set_skip_taskbar(bubble)?;
    window.set_resizable(true)?;
    if bubble {
        window.set_min_size(None::<LogicalSize<f64>>)?;
        window.set_size(LogicalSize::new(BUBBLE_WIDTH, BUBBLE_HEIGHT))?;
        window.set_resizable(false)?;
        position_bubble(window)?;
    } else {
        window.set_min_size(Some(LogicalSize::new(600.0, 480.0)))?;
        window.set_size(LogicalSize::new(960.0, 700.0))?;
        window.center()?;
    }
    window.emit("companion-mode-changed", bubble)
}

fn show(window: &WebviewWindow) -> tauri::Result<()> {
    if !window.is_visible()? {
        window.show()?;
    }
    if window.is_minimized()? {
        window.unminimize()?;
    }
    window.set_focus()
}

fn toggle_visibility(window: &WebviewWindow) -> tauri::Result<()> {
    if window.is_visible()? && !window.is_minimized()? {
        window.hide()
    } else {
        show(window)
    }
}

#[tauri::command]
fn set_companion_mode(window: WebviewWindow, bubble: bool) -> Result<(), String> {
    apply_mode(&window, bubble).map_err(|err| err.to_string())
}

#[tauri::command]
fn hide_companion(window: WebviewWindow) -> Result<(), String> {
    window.hide().map_err(|err| err.to_string())
}

fn log_window_error(result: tauri::Result<()>) {
    if let Err(err) = result {
        eprintln!("桌面窗口操作失败：{err}");
    }
}

fn main() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![set_companion_mode, hide_companion])
        .setup(|app| {
            let window = WebviewWindowBuilder::new(app, LABEL, WebviewUrl::default())
                .title(TITLE)
                .inner_size(BUBBLE_WIDTH, BUBBLE_HEIGHT)
                .transparent(true)
                .decorations(false)
                .shadow(false)
                .always_on_top(true)
                .resizable(false)
                .skip_taskbar(true)
                .visible(false)
                .build()?;

            let bubble = MenuItem::with_id(app, "bubble", "显示桌面气泡", true, None::<&str>)?;
            let chat = MenuItem::with_id(app, "chat", "打开完整窗口", true, None::<&str>)?;
            let hide = MenuItem::with_id(app, "hide", "隐藏窗口", true, None::<&str>)?;
            let divider = PredefinedMenuItem::separator(app)?;
            let quit = MenuItem::with_id(app, "quit", "退出桌面伴侣", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&bubble, &chat, &hide, &divider, &quit])?;
            let icon = app.default_window_icon().expect("需要桌面图标").clone();
            TrayIconBuilder::new()
                .icon(icon)
                .tooltip(TITLE)
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| {
                    if event.id() == "quit" {
                        app.exit(0);
                    } else if let Some(window) = app.get_webview_window(LABEL) {
                        log_window_error(match event.id().as_ref() {
                            "bubble" => apply_mode(&window, true),
                            "chat" => apply_mode(&window, false),
                            "hide" => window.hide(),
                            _ => Ok(()),
                        });
                    }
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click {
                        button: MouseButton::Left,
                        button_state: MouseButtonState::Up,
                        ..
                    } = event
                    {
                        if let Some(window) = tray.app_handle().get_webview_window(LABEL) {
                            log_window_error(toggle_visibility(&window));
                        }
                    }
                })
                .build(app)?;
            show(&window)?;
            position_bubble(&window)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                log_window_error(window.hide());
            }
        })
        .run(tauri::generate_context!())
        .expect("桌面伴侣启动失败");
}
