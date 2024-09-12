use actix_web::{get, post, web, App, Error, HttpResponse, HttpServer, Responder};
use mongodb::{
    bson::{doc, Document},
    Client, Collection,
};
use serde::{Deserialize, Serialize};
use std::sync::Arc;

#[derive(Serialize, Deserialize, Clone)]
struct User {
    name: String,
}

#[derive(Serialize, Deserialize)]
struct CreateUser {
    id: u32,
    name: String,
}

type DbCollection = Arc<Collection<Document>>;

#[get("/user/{id}")]
async fn get_user(
    user_id: web::Path<u32>,
    db: web::Data<DbCollection>,
) -> Result<impl Responder, Error> {
    let user_id = user_id.into_inner();
    let collection = db.as_ref();

    let filter = doc! { "id": user_id };
    match collection.find_one(filter).await.unwrap() {
        Some(user) => Ok(HttpResponse::Ok().json(user)),
        None => Ok(HttpResponse::NotFound().finish()),
    }
}

#[post("/users")]
async fn create_user(
    db: web::Data<DbCollection>,
    new_user: web::Json<User>,
) -> Result<impl Responder, Error> {
    let collection = db.as_ref();
    let new_id = collection.count_documents(doc! {}).await.unwrap() as u32 + 1;
    let user_with_id = doc! { "id": new_id, "name": new_user.name.clone() };
    collection.insert_one(user_with_id).await.unwrap();
    Ok(HttpResponse::Created().json(CreateUser {
        id: new_id,
        name: new_user.name.clone(),
    }))
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    let port = 8000;
    println!("The server is running on port {}", port);

    let client = Client::with_uri_str("mongodb://localhost:27017")
        .await
        .unwrap();
    let database = client.database("mydb");
    let collection = database.collection::<Document>("users");
    let db_collection = Arc::new(collection);

    HttpServer::new(move || {
        let app_data = web::Data::new(db_collection.clone());
        App::new()
            .app_data(app_data)
            .service(get_user)
            .service(create_user)
    })
    .bind(("127.0.0.1", port))?
    .workers(2)
    .run()
    .await
}
