use actix_web::{web, App, HttpResponse, HttpServer, Responder};
use lazy_static::lazy_static;
use serde::{Deserialize, Serialize};
use std::time::Instant;
use std::collections::HashMap;
use tree_sitter_highlight::Highlight;
use tree_sitter_highlight::Highlighter;
use tree_sitter_highlight::HighlightEvent;
use tree_sitter_highlight::HighlightConfiguration;
use tree_sitter_java;

const ANY: u8 = 0;
const KEYWORD: u8 = 1;
const LITERAL: u8 = 2;
const CHAR_STRING_LITERAL: u8 = 3;
const COMMENT: u8 = 4;
const CLASS_DECLARATOR: u8 = 5;
const FUNCTION_DECLARATOR: u8 = 6;
const VARIABLE_DECLARATOR: u8 = 7;
const TYPE_IDENTIFIER: u8 = 8;
const FUNCTION_IDENTIFIER: u8 = 9;
const FIELD_IDENTIFIER: u8 = 10;
const ANNOTATION_DECLARATOR: u8 = 11;

#[derive(Debug, Serialize, Deserialize)]
struct SetupData {
    lang: String
}

#[derive(Debug, Serialize, Deserialize)]
struct InputData {
    src: String
}

#[derive(Debug, Serialize, Deserialize)]
struct OutputData {
    ns: u128,
    res_json: String
}

const JAVA_HIGHLIGHT_NAMES: [&str; 13] = [
    "function.method",
    "operator",
    "attribute",
    "variable",
    "variable.builtin",
    "keyword",
    "constant.builtin",
    "string",
    "string.escape",
    "number",
    "comment",
    "type",
    "type.builtin",
];
lazy_static! {
    static ref JAVA_BINDINGS: HashMap<&'static str, u8> = {
        let mut m = HashMap::new();
        m.insert("function.method", FUNCTION_IDENTIFIER);
        m.insert("attribute", ANNOTATION_DECLARATOR);
        m.insert("variable", FIELD_IDENTIFIER);
        m.insert("variable.builtin", KEYWORD);
        m.insert("constant.builtin", KEYWORD);
        m.insert("keyword", KEYWORD);
        m.insert("string", CHAR_STRING_LITERAL);
        m.insert("number", LITERAL);
        m.insert("comment", COMMENT);
        m.insert("type", TYPE_IDENTIFIER);
        m.insert("type.builtin", KEYWORD);
        m
    };
}

fn from_tree_type(bindings: &HashMap<&'static str, u8>, tree_type: &str) -> u8 {
    *bindings.get(tree_type).unwrap_or(&ANY)
}

async fn handle_highlight(input: web::Json<InputData>) -> impl Responder {
    let mut highlighter = Highlighter::new();

    let java_language = tree_sitter_java::language();

    let mut java_config = HighlightConfiguration::new(
        java_language,
        tree_sitter_java::HIGHLIGHT_QUERY,
        "",
        ""
    ).unwrap();

    let highlight_names = JAVA_HIGHLIGHT_NAMES;
    java_config.configure(&highlight_names);

    let source = &input.src;
    eprintln!("{}", source);

    let start_time = Instant::now();
    let highlights = highlighter.highlight(
        &java_config,
        &source.as_bytes(),
        None,
        |_| None
    ).unwrap();
    let computation_ns = Instant::now().duration_since(start_time).as_nanos();

    let mut results = Vec::new();
    let mut current_index = -1;
    for event in highlights {
        match event.unwrap() {
            HighlightEvent::HighlightStart(Highlight(index)) => {
                current_index = index as i8; // Set current index to the highlight index
            }
            HighlightEvent::Source { start, end } => {
                let substring = &source[start..end];
                if current_index >= 0 {
                    let type_name = highlight_names.get(current_index as usize).unwrap();
                    let bf_type = from_tree_type(&JAVA_BINDINGS, &type_name);
                    results.push((substring.to_string(), current_index, bf_type));
                } else {
                    results.push((substring.to_string(), -1, ANY));
                }
            }
            HighlightEvent::HighlightEnd => {
                current_index = -1; // Reset current index after highlight end
            }
        }
    }
    let results_json = serde_json::to_string(&results).unwrap();

    // Access the input data using input.name
    let response_data = OutputData {
        ns: computation_ns,
        res_json: results_json
    };
    // Respond with JSON
    return HttpResponse::Ok().json(response_data)
}

async fn handle_setup(input: web::Json<SetupData>) -> impl Responder {
    if input.lang == "java" {
        return HttpResponse::Ok().json(input);
    }
    else {
        return HttpResponse::BadRequest().json(input);
    }
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    HttpServer::new(|| {
        App::new()
            .app_data(web::JsonConfig::default().limit(16 * 1024 * 1024 * 1024))
            .service(web::resource("/eval")
                .route(web::post().to(handle_highlight)))
            .service(web::resource("/setup")
                .route(web::post().to(handle_setup)))
    })
    .bind("127.0.0.1:5000")?
    .run()
    .await
}

